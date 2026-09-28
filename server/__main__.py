"""Run with python -m server; loopback by default, never auto-deploys."""

import argparse

from . import Service
from .http import make_server


def main():
    parser = argparse.ArgumentParser(description='如约本机开发服务')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', default=8765, type=int)
    parser.add_argument('--database', default='.local/pact/pact.sqlite3')
    args = parser.parse_args()
    service = Service(args.database)
    server = make_server(service, args.host, args.port)
    print(f'Pact API listening on http://{args.host}:{args.port}', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        service.close()


if __name__ == '__main__':
    main()
