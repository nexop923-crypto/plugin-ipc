//go:build windows

package raw

import (
	"github.com/netdata/plugin-ipc/go/pkg/netipc/protocol"
	windows "github.com/netdata/plugin-ipc/go/pkg/netipc/transport/windows"
)

// NewCgroupsLookupClient creates a raw client bound to the cgroups-lookup service kind.
func NewCgroupsLookupClient(runDir, serviceName string, config windows.ClientConfig) *Client {
	return newClient(runDir, serviceName, config, protocol.MethodCgroupsLookup)
}
