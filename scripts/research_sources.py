from html.parser import HTMLParser
from pathlib import Path
import urllib.request
class Text(HTMLParser):
 def __init__(self):super().__init__();self.hidden=0;self.parts=[]
 def handle_starttag(self,tag,attrs):
  if tag in ['script','style']:self.hidden+=1
  if tag in ['p','div','br','h1','h2','h3']:self.parts.append('\n')
 def handle_endtag(self,tag):
  if tag in ['script','style']:self.hidden=max(0,self.hidden-1)
 def handle_data(self,t):
  if not self.hidden:self.parts.append(t)
p=Path('build/research-v3')
for file in p.glob('*.html'):
 parser=Text();parser.feed(file.read_text('utf8'));text='\n'.join(' '.join(line.split()) for line in ''.join(parser.parts).splitlines() if line.strip());file.with_suffix('.txt').write_text(text,'utf8')
print('Extracted primary publisher descriptions; store recommendation carousels are not a ranking dataset.')
