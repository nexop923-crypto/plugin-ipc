//go:build unix

package apps_lookup

import (
	"github.com/netdata/plugin-ipc/go/pkg/netipc/service/internal/transportconfig"
	"github.com/netdata/plugin-ipc/go/pkg/netipc/transport/posix"
)

func clientConfigToTransport(config ClientConfig) posix.ClientConfig {
	return transportconfig.PosixClient(transportconfig.TypedConfig(config))
}

func serverConfigToTransport(config ServerConfig) posix.ServerConfig {
	return transportconfig.PosixServer(transportconfig.TypedConfig(config))
}
