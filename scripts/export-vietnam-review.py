"""Export the review Markdown to a printable HTML document.

Images remain relative so HTML, Markdown and PDF share the original captures.
Requires Python's markdown package; does not modify gameplay or install packages.
"""
from pathlib import Path
from html import escape
import markdown
import sys

root = Path(__file__).resolve().parents[1]
name = sys.argv[1] if len(sys.argv) > 1 else 'VIETNAM_CAMPAIGN_REVIEW'
if name not in {'VIETNAM_CAMPAIGN_REVIEW', 'VIETNAM_CAMPAIGN'}:
    raise SystemExit('Expected VIETNAM_CAMPAIGN_REVIEW or VIETNAM_CAMPAIGN')
source = root / 'docs' / (name + '.md')
title = source.read_text().splitlines()[0].removeprefix('# ')
body = markdown.markdown(source.read_text(), extensions=['tables', 'fenced_code', 'toc'])
css = '''
@page { size: A4; margin: 15mm 12mm; }
* { box-sizing: border-box; }
body { font: 15px/1.7 "DejaVu Sans", "Segoe UI", sans-serif; color:#25322e; background:#f4f2e9; margin:0; }
main { max-width:1120px; margin:32px auto; padding:48px; background:white; }
h1,h2,h3 { line-height:1.3; color:#234638; }
h1 { font-size:32px; border-bottom:4px solid #b69753; padding-bottom:20px; }
h2 { font-size:25px; margin-top:48px; padding-top:16px; border-top:1px solid #d9ddcf; }
h3 { font-size:19px; margin-top:28px; }
img { display:block; width:100%; height:auto; margin:18px auto 8px; border:1px solid #bbc7b8; }
table { width:100%; border-collapse:collapse; margin:18px 0; font-size:12px; line-height:1.6; }
th,td { border:1px solid #cdd5c7; text-align:left; vertical-align:top; padding:9px; overflow-wrap:anywhere; }
th { background:#e9edde; color:#244333; }
tr:nth-child(even) { background:#f7f8f2; }
a { color:#356955; } code { font-size:.9em; overflow-wrap:anywhere; }
pre { background:#eef1e7; padding:16px; white-space:pre-wrap; }
blockquote { border-left:4px solid #b69753; padding-left:18px; }
@media print {
 body { background:white; font-size:10px; line-height:1.55; }
 main { margin:0; padding:0; max-width:none; }
 h1 { font-size:25px; } h2 { font-size:19px; break-before:page; border-top:0; margin-top:0; }
 h3 { font-size:14px; break-after:avoid; }
 table { font-size:8px; } th,td { padding:5px; } tr { break-inside:avoid; }
 img { max-height:225mm; object-fit:contain; break-inside:avoid; }
 p { orphans:3; widows:3; } a { color:inherit; text-decoration:none; }
}
'''
target = source.with_suffix('.html')
target.write_text(f'<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>{escape(title)}</title><style>{css}</style></head><body><main>{body}</main></body></html>')
print(target)
