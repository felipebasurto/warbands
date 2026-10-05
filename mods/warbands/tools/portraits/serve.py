"""Tiny server for the 3D portrait renderer (index.html, three.js in the browser).

Run from anywhere:  python3 mods/warbands/tools/portraits/serve.py   then open http://localhost:8765/
Serves the page, the mod's entity skins (/skin/<name>.png) and vanilla item/entity textures (/tex/<file>.png, extracted
from the 26.1.2 client jar into publish/render3d/tex), and writes every PNG the page posts to publish/portraits/.
"""
import http.server
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, '..', '..'))
SKINS = os.path.join(MOD, 'src', 'main', 'resources', 'assets', 'warbands', 'textures', 'entity')
TEX = os.path.join(MOD, 'publish', 'render3d', 'tex')
OUT = os.path.join(MOD, 'publish', 'portraits')


class Handler(http.server.SimpleHTTPRequestHandler):
	def __init__(self, *a, **k):
		super().__init__(*a, directory=HERE, **k)

	def do_GET(self):
		if self.path.split('?', 1)[0] == '/raiders':
			import json
			camps = {}
			for f in sorted(os.listdir(SKINS)):
				m = re.fullmatch(r'raider_(?:(\w+?)_)?(archer|crossbowman|knight|militia|spearman|warlord)\.png', f)
				if m:
					camps.setdefault(m.group(1) or 'default', []).append(m.group(2))
			body = json.dumps(camps).encode()
			self.send_response(200)
			self.send_header('Content-Type', 'application/json')
			self.end_headers()
			self.wfile.write(body)
			return
		super().do_GET()

	def translate_path(self, path):
		path = path.split('?', 1)[0]
		for prefix, root in (('/skin/', SKINS), ('/tex/', TEX)):
			if path.startswith(prefix) and re.fullmatch(r'[\w.-]+', path[len(prefix):]):
				return os.path.join(root, path[len(prefix):])
		return super().translate_path(path)

	def do_POST(self):
		name = self.path.split('?', 1)[0].removeprefix('/save/')
		if not re.fullmatch(r'[\w-]+', name):
			self.send_error(400)
			return
		data = self.rfile.read(int(self.headers['Content-Length']))
		os.makedirs(OUT, exist_ok=True)
		with open(os.path.join(OUT, name + '.png'), 'wb') as f:
			f.write(data)
		self.send_response(200)
		self.end_headers()
		self.wfile.write(b'ok')

	def end_headers(self):
		self.send_header('Cache-Control', 'no-store')
		super().end_headers()


if __name__ == '__main__':
	http.server.ThreadingHTTPServer(('127.0.0.1', 8765), Handler).serve_forever()
