package gobridge

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/google/shlex"
	"github.com/threatexpert/gonc/v2/apps"
	"github.com/threatexpert/gonc/v2/goncembed"
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

// DiagnosticsListener receives primitive values supported by gomobile bind.
type DiagnosticsListener interface {
	DiagnosticPhase(name string, status string)
	STUNResult(server string, network string, localAddress string, publicAddress string, elapsedMillis int64, success bool, errorCode string, errorMessage string)
	NATResult(network string, natType string, localAddress string, publicAddress string)
	MQTTResult(server string, elapsedMillis int64, success bool, errorCode string, errorMessage string)
	LANResult(peerFound bool, localAddress string, peerAddress string, transport string, errorCode string, errorMessage string)
	DiagnosticsFinished(cancelled bool, succeeded int, total int)
}

type TunnelListener interface {
	Event(level string, message string)
	P2PReport(topic string, side string, status string, network string, mode string, peer string, timestamp int64, pid int, reasonCode string, reasonMessage string)
	Traffic(side string, inBytes int64, outBytes int64, inBps float64, outBps float64, elapsed int64, connCount int, final bool)
	Ready(endpoint string)
	Stopped(exitCode int)
	Error(message string)
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

var diagnosticsSessionMu sync.Mutex
var currentDiagnosticsSession *goncembed.Session

var tunnelSessionMu sync.Mutex
var currentTunnelSession *goncembed.Session

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

	StopDiagnostics()
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

	cleanArgs, err := parseGoncArgs(args)
	if err != nil {
		if androidLogger != nil {
			androidLogger.Log(fmt.Sprintf("Error parsing gonc argument string: %v", err))
		}
		return
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

func parseGoncArgs(args string) ([]string, error) {
	return shlex.Split(args)
}

// StopGonc stops the gonc execution.
func StopGonc() {
	tunnelSessionMu.Lock()
	tunnelSession := currentTunnelSession
	currentTunnelSession = nil
	tunnelSessionMu.Unlock()
	if tunnelSession != nil {
		tunnelSession.Stop()
		select {
		case <-tunnelSession.Done():
		case <-time.After(5 * time.Second):
			if androidLogger != nil {
				androidLogger.Log("StopGonc timed out waiting for embedded tunnel shutdown")
			}
		}
	}

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

// StartP2PTunnel is the structured replacement for StartGonc. It blocks until
// the embedded session exits so existing Android worker-thread lifecycles remain
// valid while callbacks carry connection stages and the actual local endpoint.
func StartP2PTunnel(password string, useUDP bool, linkConfig string, extraArgs string, listener TunnelListener) {
	if listener == nil {
		return
	}
	StopDiagnostics()
	StopGonc()
	adapter := &tunnelCallbackAdapter{listener: listener}
	session, err := goncembed.StartP2PTunnel(password, useUDP, linkConfig, extraArgs, adapter)
	if err != nil {
		listener.Error(err.Error())
		return
	}
	tunnelSessionMu.Lock()
	currentTunnelSession = session
	tunnelSessionMu.Unlock()
	<-session.Done()
	tunnelSessionMu.Lock()
	if currentTunnelSession == session {
		currentTunnelSession = nil
	}
	tunnelSessionMu.Unlock()
}

type tunnelCallbackAdapter struct {
	listener TunnelListener
}

func (a *tunnelCallbackAdapter) Event(level string, message string) {
	a.listener.Event(level, message)
}

func (a *tunnelCallbackAdapter) P2PReport(topic string, side string, status string, network string, mode string, peer string, timestamp int64, pid int) {
	reasonCode, reasonMessage := classifyTunnelStatus(status, mode)
	normalizedStatus := status
	if strings.HasPrefix(strings.ToLower(status), "error:") {
		normalizedStatus = "error"
	}
	a.listener.P2PReport(topic, side, normalizedStatus, network, mode, peer, timestamp, pid, reasonCode, reasonMessage)
}

func (a *tunnelCallbackAdapter) Traffic(side string, inBytes int64, outBytes int64, inBps float64, outBps float64, elapsed int64, connCount int, final bool) {
	a.listener.Traffic(side, inBytes, outBytes, inBps, outBps, elapsed, connCount, final)
}

func (a *tunnelCallbackAdapter) Ready(endpoint string) {
	a.listener.Ready(endpoint)
}

func (a *tunnelCallbackAdapter) Stopped(exitCode int) {
	a.listener.Stopped(exitCode)
}

func (a *tunnelCallbackAdapter) Error(message string) {
	a.listener.Error(message)
}

func classifyTunnelStatus(status, mode string) (string, string) {
	lower := strings.ToLower(status)
	if strings.EqualFold(mode, "relay") && status == "connected" {
		return "RELAY_SELECTED", "Relay path selected"
	}
	if !strings.HasPrefix(lower, "error:") {
		return "", ""
	}
	switch {
	case strings.Contains(lower, "bridge session not found"), strings.Contains(lower, "mqtt"):
		return "P2P_SIGNAL_FAILED", "P2P signaling failed"
	case strings.Contains(lower, "hole punching"), strings.Contains(lower, "punch"):
		return "P2P_PUNCH_FAILED", "P2P hole punching failed"
	case strings.Contains(lower, "socks5"):
		return "RELAY_CONNECT_FAILED", "Relay connection failed"
	case strings.Contains(lower, "timeout"):
		return "P2P_TIMEOUT", "P2P connection timed out"
	default:
		return "P2P_CONNECTION_FAILED", "P2P connection failed"
	}
}

// StartDiagnostics runs an in-process network diagnostic session. Server lists
// accept comma or newline separators; an empty list uses gonc defaults.
func StartDiagnostics(stunServers string, mqttServers string, lanSecret string, network string, timeoutMillis int, listener DiagnosticsListener) {
	if listener == nil {
		return
	}

	goncSessionMu.Lock()
	goncRunning := currentGoncSession != nil
	goncSessionMu.Unlock()
	tunnelSessionMu.Lock()
	goncRunning = goncRunning || currentTunnelSession != nil
	tunnelSessionMu.Unlock()
	if goncRunning {
		listener.DiagnosticPhase("setup", "vpn_active")
		listener.DiagnosticsFinished(false, 0, 0)
		return
	}

	StopDiagnostics()
	timeout := time.Duration(timeoutMillis) * time.Millisecond
	if timeout < time.Second {
		timeout = 6 * time.Second
	}
	if timeout > 30*time.Second {
		timeout = 30 * time.Second
	}
	session, err := goncembed.StartNetworkDiagnostics(goncembed.DiagnosticsOptions{
		STUNServers: splitServerList(stunServers),
		MQTTServers: splitServerList(mqttServers),
		LANSecret:   strings.TrimSpace(lanSecret),
		Network:     strings.TrimSpace(network),
		Timeout:     timeout,
	}, listener)
	if err != nil {
		listener.DiagnosticPhase("setup", "failed")
		listener.DiagnosticsFinished(false, 0, 0)
		return
	}
	diagnosticsSessionMu.Lock()
	currentDiagnosticsSession = session
	diagnosticsSessionMu.Unlock()
	go func() {
		<-session.Done()
		diagnosticsSessionMu.Lock()
		if currentDiagnosticsSession == session {
			currentDiagnosticsSession = nil
		}
		diagnosticsSessionMu.Unlock()
	}()
}

func StopDiagnostics() {
	diagnosticsSessionMu.Lock()
	session := currentDiagnosticsSession
	currentDiagnosticsSession = nil
	diagnosticsSessionMu.Unlock()
	if session == nil {
		return
	}
	session.Stop()
	select {
	case <-session.Done():
	case <-time.After(2 * time.Second):
	}
}

func splitServerList(raw string) []string {
	items := strings.FieldsFunc(raw, func(r rune) bool {
		return r == ',' || r == '\n' || r == '\r'
	})
	servers := make([]string, 0, len(items))
	for _, item := range items {
		if server := strings.TrimSpace(item); server != "" {
			servers = append(servers, server)
		}
	}
	return servers
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
