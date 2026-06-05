//go:build unix

package raw

import (
	"github.com/netdata/plugin-ipc/go/pkg/netipc/protocol"
	"github.com/netdata/plugin-ipc/go/pkg/netipc/transport/posix"
)

// NewIncrementClient creates a raw client bound to the increment service kind.
func NewIncrementClient(runDir, serviceName string, config posix.ClientConfig) *Client {
	return newClient(runDir, serviceName, config, protocol.MethodIncrement)
}
