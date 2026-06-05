//go:build windows

package raw

import (
	"github.com/netdata/plugin-ipc/go/pkg/netipc/protocol"
	windows "github.com/netdata/plugin-ipc/go/pkg/netipc/transport/windows"
)

// NewSnapshotClient creates a raw client bound to the cgroups-snapshot service kind.
func NewSnapshotClient(runDir, serviceName string, config windows.ClientConfig) *Client {
	return newClient(runDir, serviceName, config, protocol.MethodCgroupsSnapshot)
}
