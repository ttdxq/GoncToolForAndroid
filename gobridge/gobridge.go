package gobridge

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/threatexpert/gonc/v2/apps"
	"github.com/threatexpert/gonc/v2/misc"

	"github.com/xjasonlyu/tun2socks/v2/engine"

	_ "golang.org/x/mobile/bind"
	_ "golang.org/x/mobile/gl"
)

// Logger interface for Android to receive logs
type Logger interface {
	Log(message string)
}

type StatusListener interface {
	OnStatusChanged(status string)
}

var androidLogger Logger
var androidStatusListener StatusListener

type goncSession struct {
	cancel         context.CancelFunc
	done           chan struct{}
	shutdownMu     sync.Mutex
	nextShutdownID uint64
	shutdowns      map[uint64]func()
}

var goncSessionMu sync.Mutex
var currentGoncSession *goncSession

// SetLogger sets the logger for Android
func SetLogger(l Logger) {
	androidLogger = l
	// Redirect stdout/stderr to a custom writer that forwards to Android
	r, w, _ := os.Pipe()
	os.Stdout = w
	os.Stderr = w

	go func() {
		scanner := bufio.NewScanner(r)
		for scanner.Scan() {
			if androidLogger != nil {
				androidLogger.Log(scanner.Text())
			}
		}
	}()
}

func SetStatusListener(l StatusListener) {
	androidStatusListener = l
}

// LogWriter implements io.Writer to forward logs to Android
type LogWriter struct{}

func (w *LogWriter) Write(p []byte) (n int, err error) {
	msg := string(p)
	if androidLogger != nil {
		androidLogger.Log(strings.TrimSpace(msg))
	}
	return len(p), nil
}

// StartGonc starts the gonc P2P connection.
// This function blocks, so it should be run in a goroutine on the Android side.
func StartGonc(args string) {
	defer func() {
		if r := recover(); r != nil {
			if androidLogger != nil {
				androidLogger.Log(fmt.Sprintf("PANIC in StartGonc: %v", r))
			}
		}
	}()

	StopGonc()

	// Create context for cancellation
	ctx, cancel := context.WithCancel(context.Background())
	session := &goncSession{
		cancel:    cancel,
		done:      make(chan struct{}),
		shutdowns: make(map[uint64]func()),
	}
	goncSessionMu.Lock()
	currentGoncSession = session
	goncSessionMu.Unlock()
	defer func() {
		session.shutdownMu.Lock()
		session.shutdowns = nil
		session.shutdownMu.Unlock()
		goncSessionMu.Lock()
		if currentGoncSession == session {
			currentGoncSession = nil
		}
		goncSessionMu.Unlock()
		close(session.done)
	}()

	argSlice := strings.Split(args, " ")
	// Filter empty strings if any
	var cleanArgs []string
	// cleanArgs = append(cleanArgs, "gonc") // argv0 is handled by AppNetcatConfigByArgs? No, typically args[0] is progname.
	// Check apps.nc usage. Main calls it with os.Args[1:].
	// App_Netcat_main calls AppNetcatConfigByArgs(logWriter, "gonc", args).
	// usage: config, err := AppNetcatConfigByArgs(logWriter, "gonc", args)

	for _, arg := range argSlice {
		if strings.TrimSpace(arg) != "" {
			cleanArgs = append(cleanArgs, arg)
		}
	}

	console := &misc.ConsoleIO{}

	// Manually parse args and run to inject context
	config, err := apps.AppNetcatConfigByArgs(console, "gonc", cleanArgs)
	if err != nil {
		if androidLogger != nil {
			androidLogger.Log(fmt.Sprintf("Error parsing gonc args: %v", err))
		}
		return
	}
	config.ConsoleMode = true
	config.Ctx = ctx
	config.GlobalCtx = ctx
	config.Callback_RegisterShutdown = func(closeFunc func()) func() {
		session.shutdownMu.Lock()
		if closeFunc == nil {
			session.shutdownMu.Unlock()
			return func() {}
		}
		id := session.nextShutdownID
		session.nextShutdownID++
		session.shutdowns[id] = closeFunc
		session.shutdownMu.Unlock()

		return func() {
			session.shutdownMu.Lock()
			if session.shutdowns != nil {
				delete(session.shutdowns, id)
			}
			session.shutdownMu.Unlock()
		}
	}
	config.Callback_OnSessionReady = func() {
		if androidStatusListener != nil {
			androidStatusListener.OnStatusChanged("connected")
		}
	}

	apps.App_Netcat_main_withconfig(console, config)
}

// StopGonc stops the gonc execution.
func StopGonc() {
	goncSessionMu.Lock()
	session := currentGoncSession
	goncSessionMu.Unlock()
	if session == nil {
		return
	}

	session.shutdownMu.Lock()
	shutdowns := make([]func(), 0, len(session.shutdowns))
	for _, shutdown := range session.shutdowns {
		shutdowns = append(shutdowns, shutdown)
	}
	session.shutdownMu.Unlock()
	for _, shutdown := range shutdowns {
		shutdown()
	}

	session.cancel()

	select {
	case <-session.done:
	case <-time.After(5 * time.Second):
		if androidLogger != nil {
			androidLogger.Log("StopGonc timed out waiting for gonc shutdown")
		}
	}
}

// StartTun2Socks starts the tun2socks engine with the given file descriptor.
// fd: The file descriptor of the TUN interface (as an int).
// proxyUrl: The SOCKS5 proxy URL (e.g., "socks5://127.0.0.1:1080").
// deviceName: The name of the device (not strictly needed for fd:// but good for logging).
// mtu: The MTU of the interface.
func StartTun2Socks(fd int, proxyUrl string, deviceName string, mtu int, logLevel string) {
	defer func() {
		if r := recover(); r != nil {
			if androidLogger != nil {
				androidLogger.Log(fmt.Sprintf("PANIC in StartTun2Socks: %v", r))
			}
		}
	}()
	key := &engine.Key{
		Device:   fmt.Sprintf("fd://%d", fd),
		Proxy:    proxyUrl,
		LogLevel: logLevel,
		MTU:      mtu,
	}

	engine.Insert(key)
	engine.Start()
}

// StopTun2Socks stops the tun2socks engine.
func StopTun2Socks() {
	defer func() {
		if r := recover(); r != nil {
			if androidLogger != nil {
				androidLogger.Log(fmt.Sprintf("PANIC in StopTun2Socks: %v", r))
			}
		}
	}()
	engine.Stop()
}
