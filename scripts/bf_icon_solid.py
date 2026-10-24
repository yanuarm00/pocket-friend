# Kepala BF versi ikon "filled": siluet blok, mata & mulut = garis yang dilubangi (cut-out).
# Keluaran: satu path (fillType evenOdd) di koordinat 0..W x 0..H. Ganti SIL/mata/mulut di sini saat karakter final.
from shapely.geometry import Polygon, LineString, Point
from shapely.ops import unary_union
from shapely import affinity
import math, sys
X0, Y0 = 130.977, 71.52
def cubic(p0,p1,p2,p3,n=12):
    return [tuple(((1-t)**3)*a+3*((1-t)**2)*t*b+3*(1-t)*t*t*c+t**3*d for a,b,c,d in zip(p0,p1,p2,p3)) for t in [i/n for i in range(1,n+1)]]
def parse(d):
    import re
    tk=re.findall(r'[MLCZ]|-?[\d.]+',d); i=0; pts=[]; cur=None; cmd=None
    while i<len(tk):
        if tk[i] in 'MLCZ': cmd=tk[i]; i+=1
        if cmd=='Z': continue
        if cmd=='M' or cmd=='L':
            cur=(float(tk[i]),float(tk[i+1])); i+=2; pts.append(cur)
        elif cmd=='C':
            a=(float(tk[i]),float(tk[i+1]));b=(float(tk[i+2]),float(tk[i+3]));c=(float(tk[i+4]),float(tk[i+5])); i+=6
            pts+=cubic(cur,a,b,c); cur=c
    return pts
SIL="M134.79 71.52 C133.799 71.52 132.857 71.901 132.139 72.594 C131.39 73.316 130.977 74.29 130.977 75.335 L130.977 93.963 C130.977 98.839 134.944 102.807 139.821 102.807 L151.13 102.807 C156.006 102.807 159.973 98.839 159.973 93.963 L159.973 82.654 C159.973 77.778 156.006 73.81 151.13 73.81 L140.29 73.81 L136.486 71.921 C135.951 71.655 135.381 71.52 134.79 71.52 Z"
MOUTH="M140.647 94.88 C142.21 96.443 144.741 96.443 146.304 94.88"
def build(sw=2.0):
    sil=Polygon(parse(SIL))
    cut=[]
    for cx in (139.935,147.288):
        pill=LineString([(cx,81.644+2.415),(cx,81.644+9.658-2.415)]).buffer(2.415,32)
        cut.append(pill.exterior.buffer(sw/2,16))
    cut.append(LineString(parse(MOUTH)).buffer(sw/2,16))
    g=sil.difference(unary_union(cut))
    g=affinity.translate(g,-X0,-Y0)
    return g
def to_d(g):
    polys=[g] if g.geom_type=='Polygon' else list(g.geoms); out=[]
    for p in polys:
        for ring in [p.exterior,*p.interiors]:
            c=list(ring.coords)[:-1]
            out.append('M'+' L'.join(f'{x:.3f} {y:.3f}' for x,y in c)+' Z')
    return ' '.join(out)
if __name__=='__main__':
    print(to_d(build(float(sys.argv[1]) if len(sys.argv)>1 else 2.0)))
