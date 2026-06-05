//go:build windows

package raw

import (
	"github.com/netdata/plugin-ipc/go/pkg/netipc/protocol"
	windows "github.com/netdata/plugin-ipc/go/pkg/netipc/transport/windows"
)

// NewIncrementClient creates a raw client bound to the increment service kind.
func NewIncrementClient(runDir, serviceName string, config windows.ClientConfig) *Client {
	return newClient(runDir, serviceName, config, protocol.MethodIncrement)
}
