#!/usr/bin/env python3
# Ikon PNG-k generalasa (public/icon.svg alapjan), kulso fuggoseg nelkul.
import zlib, struct, os
BG=(0x1a,0x19,0x15); FG=(0xd9,0x77,0x57)
STAR=[(256,76),(294,208),(426,246),(294,284),(256,416),(218,284),(86,246),(218,208)]
def inside_poly(x,y,p):
    c=False; n=len(p)
    for i in range(n):
        x1,y1=p[i]; x2,y2=p[(i+1)%n]
        if (y1>y)!=(y2>y) and x < (x2-x1)*(y-y1)/(y2-y1)+x1: c=not c
    return c
def inside_rr(x,y,r=110,s=512):
    cx=min(max(x,r),s-r); cy=min(max(y,r),s-r)
    return (x-cx)**2+(y-cy)**2<=r*r and 0<=x<=s and 0<=y<=s
def png(size,path,round_=True,ss=4):
    rows=[]
    for py in range(size):
        row=bytearray([0])
        for px in range(size):
            a=0; fg=0
            for sy in range(ss):
                for sx in range(ss):
                    x=(px+(sx+.5)/ss)*512/size; y=(py+(sy+.5)/ss)*512/size
                    if (not round_) or inside_rr(x,y):
                        a+=1
                        if inside_poly(x,y,STAR): fg+=1
            n=ss*ss
            if a==0: row+=bytes(4); continue
            t=fg/a
            col=[round(BG[i]*(1-t)+FG[i]*t) for i in range(3)]
            row+=bytes(col+[round(255*a/n)])
        rows.append(bytes(row))
    raw=b''.join(rows)
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
    os.makedirs(os.path.dirname(path),exist_ok=True)
    open(path,'wb').write(data)
for d,s in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    png(s,f'res/mipmap-{d}/ic_launcher.png')
png(256,'res/drawable-nodpi/splash_icon.png')
