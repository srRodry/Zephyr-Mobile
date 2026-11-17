"""Build the official Zephyr-Mobile landing page, without runtime/build dependencies."""
from pathlib import Path
import shutil,json,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
out=root/'dist'
if out.exists():shutil.rmtree(out)
out.mkdir()
for name in ('index.html','style.css','site.js'):shutil.copy(root/'anel'/name,out/name)
shutil.copytree(root/'src/assets/brand',out/'assets/brand')
(out/'site.webmanifest').write_text(json.dumps({'name':'Zephyr-Mobile','short_name':'Zephyr','start_url':'./','scope':'./','display':'standalone','background_color':'#04150F','theme_color':'#04150F','icons':[{'src':f'assets/brand/icon-{n}.png','sizes':f'{n}x{n}','type':'image/png','purpose':'any'} for n in (192,512)]}))
shutil.copy(out/'index.html',out/'404.html')
print('Zephyr-Mobile site built:',out)
