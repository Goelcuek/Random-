import math, os, sys, io
import cairosvg
from PIL import Image

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

def squircle(n=5.0, r=250, c=256, pts=240):
    p = []
    for i in range(pts):
        t = 2*math.pi*i/pts
        co, si = math.cos(t), math.sin(t)
        x = c + r*math.copysign(abs(co)**(2/n), co)
        y = c + r*math.copysign(abs(si)**(2/n), si)
        p.append(f"{x:.1f},{y:.1f}")
    return "M" + " L".join(p) + " Z"
SQ = squircle()

def grad(name, a, b):
    return f'<linearGradient id="{name}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>'
DEFS = "".join([
    grad("green", "#A6EE94", "#46C257"), grad("blue", "#7DBBFF", "#3A6FE0"),
    grad("red", "#FF9082", "#DD4036"), grad("orange", "#FFCB78", "#F08428"),
    grad("purple", "#CFA6FF", "#7A52E4"), grad("yellow", "#F8E789", "#D6AE36"),
    grad("white", "#FFFFFF", "#B9BECA"), grad("pink", "#FFA3C8", "#DE4C88"),
    grad("teal", "#86E8DB", "#28AD9F"),
    '<linearGradient id="tile" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#39393d"/><stop offset="1" stop-color="#202023"/></linearGradient>',
    '<linearGradient id="gloss" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="0.10"/><stop offset="0.5" stop-color="#fff" stop-opacity="0"/></linearGradient>',
    f'<clipPath id="clip"><path d="{SQ}"/></clipPath>',
])

def mat(path, fill, s=12.5, dx=0, dy=0):
    return f'<g transform="translate({256+dx},{256+dy}) scale({s}) translate(-12,-12)"><path d="{path}" fill="url(#{fill})"/></g>'

def rrect(x,y,w,h,r,fill=None,stroke=None,sw=0,extra=""):
    f = f'fill="url(#{fill})"' if fill else 'fill="none"'
    s = f'stroke="url(#{stroke})" stroke-width="{sw}"' if stroke else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" {f} {s} {extra}/>'

def circ(cx,cy,r,fill=None,stroke=None,sw=0):
    f = f'fill="url(#{fill})"' if fill else 'fill="none"'
    s = f'stroke="url(#{stroke})" stroke-width="{sw}"' if stroke else ""
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" {f} {s}/>'

SOLID = {"white":"#E9ECF3","blue":"#5A96F2","orange":"#F59A3E","yellow":"#E9C654","pink":"#EE6FA3","red":"#EE5A4D","green":"#5CCB68"}
def line(x1,y1,x2,y2,stroke,sw):
    col = SOLID.get(stroke, stroke)
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}" stroke-width="{sw}" stroke-linecap="round"/>'

P = {}  # glyph name -> svg body
P["phone"] = mat("M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z","green",13)
P["contacts"] = mat("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z","green",14)
P["messages"] = (rrect(110,120,230,170,38,"green") + rrect(190,200,230,170,38,"green").replace('<rect','<rect opacity="0.9"')
                 + '<path d="M110 300 L110 340 L160 300 Z" fill="url(#green)"/>')
teeth = "".join(f'<rect x="236" y="92" width="40" height="64" rx="10" fill="url(#white)" transform="rotate({a} 256 256)"/>' for a in range(0,360,45))
P["settings"] = teeth + circ(256,256,104,"white") + circ(256,256,48,fill=None).replace('fill="none"','fill="#26262a"') + circ(256,256,22,"white")
P["camera"] = circ(256,256,138,stroke="white",sw=26) + circ(256,256,86,"purple").replace('url(#purple)','#17171c') + circ(256,256,44,"blue") + circ(380,142,16,"orange")
P["gallery"] = (rrect(100,130,312,252,40,"yellow") + circ(340,200,28,"white") +
                '<path d="M100 340 L200 230 L270 310 L320 260 L412 350 L412 342 Q412 382 372 382 L140 382 Q100 382 100 342 Z" fill="#26262a" opacity="0.55"/>')
P["music"] = mat("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z","red",15)
hands = line(256,256,256,160,"white",22) + line(256,256,320,290,"white",22)
P["clock"] = circ(256,256,140,stroke="white",sw=24) + hands + circ(256,256,14,"orange")
cal_dots = "".join(circ(160+i*64, 270+j*56, 13, "white") for i in range(4) for j in range(2))
P["calendar"] = rrect(102,110,308,292,46,"white") + '<path d="M102 156 Q102 110 148 110 L364 110 Q410 110 410 156 L410 190 L102 190 Z" fill="url(#red)"/>' + "".join(circ(160+i*64, 270+j*56, 13, "red") for i in range(4) for j in range(2)).replace('url(#red)','#d84a40')
P["calculator"] = rrect(120,96,272,320,44,stroke="white",sw=0) + rrect(120,96,272,320,44,"orange") + rrect(152,128,208,70,14).replace('fill="none"','fill="#26262a"') + "".join(rrect(152+i*74,224+j*62,54,44,12).replace('fill="none"','fill="#fff" fill-opacity="0.9"') for i in range(3) for j in range(2))
P["files"] = mat("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z","blue",14)
P["notes"] = (rrect(118,96,276,320,34,"yellow") + "".join(line(166,190+i*54,346,190+i*54,"#8a6d1c",16) for i in range(4)))
P["weather"] = (circ(206,200,62,"yellow") + "".join(line(206+88*math.cos(a),200+88*math.sin(a),206+112*math.cos(a),200+112*math.sin(a),"yellow",18) for a in [i*math.pi/4 for i in range(8)])
                + '<path d="M170 380 Q112 380 112 330 Q112 282 168 278 Q184 236 238 240 Q290 236 304 280 Q380 276 394 330 Q396 380 340 380 Z" fill="url(#white)"/>')
P["maps"] = mat("M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z","red",16)
P["store"] = mat("M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm0 10c-2.76 0-5-2.24-5-5h2c0 1.66 1.34 3 3 3s3-1.34 3-3h2c0 2.76-2.24 5-5 5z","blue",14)
P["globe"] = (circ(256,256,142,stroke="blue",sw=22) + '<ellipse cx="256" cy="256" rx="62" ry="142" fill="none" stroke="url(#blue)" stroke-width="20"/>'
              + line(114,256,398,256,"blue",20) + line(138,184,374,184,"blue",16) + line(138,328,374,328,"blue",16))
P["ring"] = '<ellipse cx="256" cy="256" rx="104" ry="146" fill="none" stroke="url(#red)" stroke-width="46"/>'
P["video"] = rrect(88,138,336,236,60,"red") + '<path d="M222 200 L222 312 L318 256 Z" fill="#26262a"/>'
P["mail"] = mat("M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z","red",15)
P["chat"] = rrect(106,106,300,250,64,"green") + '<path d="M150 350 L132 414 L210 356 Z" fill="url(#green)"/>' + circ(196,232,18).replace('fill="none"','fill="#26262a"') + circ(256,232,18).replace('fill="none"','fill="#26262a"') + circ(316,232,18).replace('fill="none"','fill="#26262a"')
P["plane"] = mat("M2.01 21L23 12 2.01 3 2 10l15 2-15 2z","blue",14)
P["insta"] = rrect(112,112,288,288,82,stroke="pink",sw=26) + circ(256,256,66,stroke="orange",sw=26) + circ(344,168,14,"pink")
P["spotify"] = circ(256,256,146,"green") + "".join(f'<path d="M{150+dx} {212+k*52} Q256 {180+k*52} {362-dx} {212+k*52}" fill="none" stroke="#26262a" stroke-width="{26-k*3}" stroke-linecap="round"/>' for k,dx in enumerate([0,16,34]))
P["learn"] = mat("M5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82zM12 3L1 9l11 6 9-4.91V17h2V9L12 3z","green",15)
P["movie"] = mat("M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z","red",15)
P["gamepad"] = rrect(84,170,344,190,92,"purple") + rrect(152,226,22,70,8).replace('fill="none"','fill="#26262a"') + rrect(128,250,70,22,8).replace('fill="none"','fill="#26262a"') + circ(332,238,16).replace('fill="none"','fill="#26262a"') + circ(372,278,16).replace('fill="none"','fill="#26262a"')
P["hash"] = "".join(line(*c,"white",26) for c in [(210,120,180,392),(332,120,302,392),(116,208,408,208),(104,304,396,304)])
P["short"] = rrect(150,96,212,320,48,"pink") + '<path d="M232 206 L232 306 L312 256 Z" fill="#26262a"/>'
P["like"] = mat("M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z","blue",13)
P["drive"] = '<path d="M196 110 L316 110 L416 290 L356 290 L256 110Z" fill="url(#yellow)"/><path d="M196 110 L96 290 L156 390 L256 214Z" fill="url(#green)"/><path d="M156 390 L356 390 L416 290 L216 290Z" fill="url(#blue)"/>'
P["sparkle"] = '<path d="M256 84 C266 190 322 246 428 256 C322 266 266 322 256 428 C246 322 190 266 84 256 C190 246 246 190 256 84Z" fill="url(#purple)"/>'
P["search"] = mat("M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z","blue",16)
P["leaf"] = mat("M17 8C8 10 5.9 16.17 3.82 21.34l1.89.66.95-2.3c.48.17.98.3 1.34.3C19 20 22 3 22 3c-1 2-8 2.25-13 3.25S2 11.5 2 13.5s1.75 3.75 1.75 3.75C7 8 17 8 17 8z","teal",14)

ICONS = [  # drawable, label, glyph, [components]
 ("play_store","Play Store","store",["com.android.vending/com.android.vending.AssetBrowserActivity"]),
 ("chrome","Chrome","globe",["com.android.chrome/com.google.android.apps.chrome.Main"]),
 ("opera","Opera","ring",["com.opera.browser/com.opera.Opera"]),
 ("duolingo","Duolingo","learn",["com.duolingo/com.duolingo.app.LoginActivity"]),
 ("youtube","YouTube","video",["com.google.android.youtube/com.google.android.youtube.HomeActivity"]),
 ("gmail","Gmail","mail",["com.google.android.gm/com.google.android.gm.ConversationListActivityGmail"]),
 ("google_maps","Maps","maps",["com.google.android.apps.maps/com.google.android.maps.MapsActivity"]),
 ("google_photos","Photos","gallery",["com.google.android.apps.photos/com.google.android.apps.photos.home.HomeActivity"]),
 ("google","Google","search",["com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity"]),
 ("google_drive","Drive","drive",["com.google.android.apps.docs/com.google.android.apps.docs.drive.startup.StartupActivity"]),
 ("gemini","Gemini","sparkle",["com.google.android.apps.bard/com.google.android.apps.bard.shellapp.BardEntryPointActivity"]),
 ("whatsapp","WhatsApp","chat",["com.whatsapp/com.whatsapp.Main"]),
 ("telegram","Telegram","plane",["org.telegram.messenger/org.telegram.ui.LaunchActivity"]),
 ("instagram","Instagram","insta",["com.instagram.android/com.instagram.mainactivity.LoginActivity"]),
 ("spotify","Spotify","spotify",["com.spotify.music/com.spotify.music.MainActivity"]),
 ("netflix","Netflix","movie",["com.netflix.mediaclient/com.netflix.mediaclient.ui.launch.UIWebViewActivity"]),
 ("discord","Discord","gamepad",["com.discord/com.discord.app.AppActivity$Main"]),
 ("x","X","hash",["com.twitter.android/com.twitter.android.StartActivity"]),
 ("tiktok","TikTok","short",["com.zhiliaoapp.musically/com.ss.android.ugc.aweme.splash.SplashActivity"]),
 ("facebook","Facebook","like",["com.facebook.katana/com.facebook.katana.LoginActivity"]),
 ("g_calendar","Google Calendar","calendar",["com.google.android.calendar/com.android.calendar.AllInOneActivity"]),
 ("g_calculator","Google Calculator","calculator",["com.google.android.calculator/com.android.calculator2.Calculator"]),
 ("g_clock","Google Clock","clock",["com.google.android.deskclock/com.android.deskclock.DeskClock"]),
 ("g_messages","Google Messages","messages",["com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity"]),
 ("g_phone","Google Phone","phone",["com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity"]),
 ("g_contacts","Google Contacts","contacts",["com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]),
 ("g_files","Files by Google","files",["com.google.android.apps.nbu.files/com.google.android.apps.nbu.files.home.HomeActivity"]),
 ("settings","Settings","settings",["com.android.settings/com.android.settings.Settings"]),
 # Honor / MagicOS system apps (activity names are best guesses)
 ("honor_phone","Phone","phone",["com.hihonor.contacts/com.hihonor.contacts.activities.DialtactsActivity","com.android.contacts/com.android.contacts.activities.DialtactsActivity"]),
 ("honor_contacts","Contacts","contacts",["com.hihonor.contacts/com.hihonor.contacts.activities.PeopleActivity","com.android.contacts/com.android.contacts.activities.PeopleActivity"]),
 ("honor_messages","Messages","messages",["com.hihonor.mms/com.hihonor.mms.ui.ConversationList","com.android.mms/com.android.mms.ui.ConversationList"]),
 ("honor_camera","Camera","camera",["com.hihonor.camera/com.hihonor.camera.CameraActivity","com.huawei.camera/com.huawei.camera"]),
 ("honor_gallery","Gallery","gallery",["com.hihonor.photos/com.hihonor.photos.app.PhotosActivity","com.android.gallery3d/com.huawei.gallery.app.GalleryMain"]),
 ("honor_music","Music","music",["com.hihonor.music/com.hihonor.music.ui.MainActivity"]),
 ("honor_clock","Clock","clock",["com.hihonor.deskclock/com.hihonor.deskclock.main.DeskClockApp"]),
 ("honor_calendar","Calendar","calendar",["com.hihonor.calendar/com.hihonor.calendar.AllInOneActivity"]),
 ("honor_calculator","Calculator","calculator",["com.hihonor.calculator/com.hihonor.calculator.Calculator"]),
 ("honor_notes","Notes","notes",["com.hihonor.notepad/com.hihonor.notepad.ui.NotePadMainActivity"]),
 ("honor_files","Files","files",["com.hihonor.filemanager/com.hihonor.filemanager.FileManagerMain"]),
 ("honor_weather","Weather","weather",["com.hihonor.android.weather/com.hihonor.android.totemweather.WeatherHome"]),
 ("honor_settings","Settings","settings",["com.android.settings/com.android.settings.HWSettings"]),
 ("honor_health","Health","leaf",["com.hihonor.health/com.hihonor.health.MainActivity"]),
]

def svg_for(glyph):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><defs>{DEFS}</defs>'
            f'<path d="{SQ}" fill="url(#tile)"/>'
            f'<g clip-path="url(#clip)"><path d="{SQ}" fill="url(#gloss)"/></g>'
            f'<path d="{SQ}" fill="none" stroke="#fff" stroke-opacity="0.07" stroke-width="2"/>'
            f'<g transform="translate(256,256) scale(1.22) translate(-256,-256)">{P[glyph]}</g></svg>')

if __name__ == "__main__":
    res = os.path.join(OUT, "drawable-nodpi"); os.makedirs(res, exist_ok=True)
    for name, label, glyph, comps in ICONS:
        cairosvg.svg2png(bytestring=svg_for(glyph).encode(), write_to=os.path.join(res, name + ".png"), output_width=256, output_height=256)
    # appfilter + drawable lists
    af = ['<?xml version="1.0" encoding="utf-8"?>','<resources>','    <scale factor="1.0" />']
    for name, label, glyph, comps in ICONS:
        for c in comps:
            af.append(f'    <item component="ComponentInfo{{{c}}}" drawable="{name}" />')
    af.append('</resources>')
    xmld = os.path.join(OUT, "xml"); os.makedirs(xmld, exist_ok=True)
    open(os.path.join(xmld, "appfilter.xml"), "w").write("\n".join(af))
    dr = ['<?xml version="1.0" encoding="utf-8"?>','<resources>','    <version>1</version>','    <category title="All" />']
    for name, *_ in ICONS: dr.append(f'    <item drawable="{name}" />')
    dr.append('</resources>')
    open(os.path.join(xmld, "drawable.xml"), "w").write("\n".join(dr))
    # contact sheet
    cols = 8; rows = math.ceil(len(ICONS)/cols); cs = 160
    sheet = Image.new("RGB", (cols*cs, rows*cs), (14,10,30))
    for i,(name,*_) in enumerate(ICONS):
        im = Image.open(os.path.join(res, name + ".png")).convert("RGBA").resize((136,136), Image.LANCZOS)
        sheet.paste(im, ((i%cols)*cs+12, (i//cols)*cs+12), im)
    sheet.save("/home/claude/tools/preview.png")
    print(len(ICONS), "icons")
