//go:build windows

package raw

import (
	"github.com/netdata/plugin-ipc/go/pkg/netipc/protocol"
	windows "github.com/netdata/plugin-ipc/go/pkg/netipc/transport/windows"
)

// NewAppsLookupClient creates a raw client bound to the apps-lookup service kind.
func NewAppsLookupClient(runDir, serviceName string, config windows.ClientConfig) *Client {
	return newClient(runDir, serviceName, config, protocol.MethodAppsLookup)
}
