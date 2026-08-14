package gobridge

import (
	"reflect"
	"testing"
)

func TestSplitServerList(t *testing.T) {
	got := splitServerList(" udp://one:1,\n tcp://two:2 ,, tls://three:3 ")
	want := []string{"udp://one:1", "tcp://two:2", "tls://three:3"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("splitServerList() = %#v, want %#v", got, want)
	}
}

func TestParseGoncArgsPreservesQuotedAndEscapedValues(t *testing.T) {
	got, err := parseGoncArgs(`-p2p "secret with spaces" -stunsrv "udp://one:3478, udp://two:3478" -x value\ with\ spaces`)
	if err != nil {
		t.Fatal(err)
	}
	want := []string{
		"-p2p", "secret with spaces",
		"-stunsrv", "udp://one:3478, udp://two:3478",
		"-x", "value with spaces",
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("parseGoncArgs() = %#v, want %#v", got, want)
	}
}

func TestParseGoncArgsRejectsUnclosedQuote(t *testing.T) {
	if _, err := parseGoncArgs(`-p2p "secret with spaces`); err == nil {
		t.Fatal("parseGoncArgs() returned nil error for an unclosed quote")
	}
}

func TestClassifyTunnelStatus(t *testing.T) {
	tests := []struct {
		status string
		mode   string
		want   string
	}{
		{status: "connected", mode: "Relay", want: "RELAY_SELECTED"},
		{status: "error: P2P UDP hole punching failed", want: "P2P_PUNCH_FAILED"},
		{status: "error: bridge session not found", want: "P2P_SIGNAL_FAILED"},
		{status: "error: timeout", want: "P2P_TIMEOUT"},
		{status: "connecting", want: ""},
	}
	for _, test := range tests {
		got, _ := classifyTunnelStatus(test.status, test.mode)
		if got != test.want {
			t.Errorf("classifyTunnelStatus(%q, %q) = %q, want %q", test.status, test.mode, got, test.want)
		}
	}
}
