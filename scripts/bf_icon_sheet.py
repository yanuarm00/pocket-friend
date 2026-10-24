# Render icon_preview.png & bf_icon_v3b.png (ikon BF filled vs ikon nav Material Filled + launcher).
import sys,io,re; sys.path.insert(0,'scripts')
import cairosvg
from PIL import Image,ImageDraw,ImageFont
from bf_icon_solid import build,to_d
D=to_d(build(2.0).simplify(0.015)); s=20/31.287
BF=f'<g transform="translate({(24-28.996*s)/2:.3f} 2) scale({s:.5f})"><path fill-rule="evenodd" d="{D}"/></g>'
P=[('Beranda','<path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>'),('ASK BF',BF),
('TO-DO','<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.170l7.59-7.59L19 8l-9 9z"/>'),
('Notes','<path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>'),
('Pengaturan','<path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>')]
def svg(inner,px,fg,vb=24,bg=None,clip=False):
    b=f'<rect width="{vb}" height="{vb}" fill="{bg}"/>' if bg else ''
    x=f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="0 0 {vb} {vb}">'+(f'<clipPath id="c"><circle cx="{vb/2}" cy="{vb/2}" r="{vb/2}"/></clipPath><g clip-path="url(#c)">' if clip else '<g>')+b+f'<g fill="{fg}">{inner}</g></g></svg>'
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=x.encode()))).convert('RGBA')
fg=open('app/src/main/res/drawable/ic_launcher_foreground.xml').read()
g=re.search(r'translateX="([\d.]+)" android:translateY="([\d.]+)" android:scaleX="([\d.]+)"',fg).groups()
LI=f'<g transform="translate({g[0]} {g[1]}) scale({g[2]})"><path fill-rule="evenodd" d="{D}"/></g>'
launcher=lambda px,m=False: svg(LI,px,'#1F2A36' if m else '#FFFFFF',108,'#D6E3F0' if m else '#000000',True)
F='/workspace/devy/bf01/ui_v6/fonts/Lexend-VariableFont_wght.ttf'; f=lambda n:ImageFont.truetype(F,n)
ip=Image.new('RGB',(900,380),'#f2f2f2'); d=ImageDraw.Draw(ip)
for i,(m,l) in enumerate([(False,'Launcher'),(True,'Themed (monochrome)')]):
    L=launcher(288,m); ip.paste(L,(60+i*420,30),L); d.text((60+i*420,335),l,font=f(24),fill='black')
ip.save('screens_pf/icon_preview.png')
SC=4; slot=48*SC+60; PW=200+5*slot; W=PW+80+380; im=Image.new('RGB',(W,2000),'#e9e9e9'); d=ImageDraw.Draw(im)
d.text((40,25),'BF icon v3b - blok solid, mata & mulut outline, vs ikon nav Material Filled',font=f(32),fill='black')
y=95
for mode,bg,c in [('Dark','#000000','#FFFFFF'),('Light','#FFFFFF','#000000')]:
    for dp in (24,48):
        h=dp*SC+90; d.rounded_rectangle((40,y,40+PW,y+h),24,fill=bg); d.text((60,y+14),f'{mode}\n{dp}dp',font=f(24),fill=c)
        for i,(k,v) in enumerate(P):
            x=200+i*slot+(slot-dp*SC)//2; ic=svg(v,dp*SC,c); im.paste(ic,(x,y+25),ic)
            tw=d.textlength(k,font=f(20)); d.text((200+i*slot+(slot-tw)/2,y+35+dp*SC),k,font=f(20),fill=c)
        y+=h+20
X=PW+90
for i,(m,l) in enumerate([(False,'Launcher'),(True,'Themed icon (monochrome)')]):
    L=launcher(320,m); im.paste(L,(X,100+i*420),L); d.text((X,430+i*420),l,font=f(24),fill='black')
im.crop((0,0,W,max(y,900)+20)).save('screens_pf/bf_icon_v3b.png')
