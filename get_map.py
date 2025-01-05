import folium
import sqlite3
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders

# 데이터베이스에서 위도, 경도, name 데이터 가져오기
def get_location_data():
    conn = sqlite3.connect("location.db")
    cur = conn.cursor()

    cur.execute("SELECT latitude, longitude, name FROM location_data")
    data = cur.fetchall()

    conn.close()
    return data

# 대구 중심의 위도, 경도
center_latitude, center_longitude = 35.8936, 128.8500

# 데이터베이스에서 가져온 위치 데이터를 지도에 표시
location_data = get_location_data()

# Separate data based on the "name" column
humulus_data = [(lat, lon) for lat, lon, name in location_data if name == "Humulus japonicus Siebold"]
sicyos_data = [(lat, lon) for lat, lon, name in location_data if name == "Sicyos angulatus"]
lettuce_data = [(lat, lon) for lat, lon, name in location_data if name == "Prickly lettuce"]

# 대구 지도 생성 - Humulus japonicus Siebold
map_humulus = folium.Map(location=[center_latitude, center_longitude], zoom_start=12)
for latitude, longitude in humulus_data:
    folium.Marker([latitude, longitude], popup='Location').add_to(map_humulus)
map_humulus.save('Humulus_japonicus_Siebold.html')

# 대구 지도 생성 - Sicyos angulatus
map_sicyos = folium.Map(location=[center_latitude, center_longitude], zoom_start=12)
for latitude, longitude in sicyos_data:
    folium.Marker([latitude, longitude], popup='Location').add_to(map_sicyos)
map_sicyos.save('Sicyos_angulatus.html')

# 대구 지도 생성 - prickly lettuce
map_lettuce = folium.Map(location=[center_latitude, center_longitude], zoom_start=12)
for latitude, longitude in lettuce_data:
    folium.Marker([latitude, longitude], popup='Location').add_to(map_lettuce)
map_lettuce.save('Prickly_lettuce.html')


# 보내는 이메일 계정 설정
sender_email = ""
sender_password = ""

# 받는 이메일 주소
receiver_email = ""

# 메일 제목 및 내용
subject = "Daegu Maps"
body = "Attached are the Daegu Maps HTML files."

# 첨부 파일 경로들
attachment_paths = ["Humulus_japonicus_Siebold.html", "Sicyos_angulatus.html", "Prickly_lettuce.html"]

# MIME 설정
message = MIMEMultipart()
message["From"] = sender_email
message["To"] = receiver_email
message["Subject"] = subject
message.attach(MIMEText(body, "plain"))

# 파일 첨부
for attachment_path in attachment_paths:
    with open(attachment_path, "rb") as attachment:
        part = MIMEBase("application", "octet-stream")
        part.set_payload(attachment.read())
        encoders.encode_base64(part)
        part.add_header("Content-Disposition", f"attachment; filename={attachment_path}")  # Explicitly set the filename
        message.attach(part)

# SMTP 서버 설정 및 연결
smtp_server = "smtp.gmail.com"
smtp_port = 587
server = smtplib.SMTP(smtp_server, smtp_port)
server.starttls()

# 로그인
server.login(sender_email, sender_password)

# 이메일 전송
server.sendmail(sender_email, receiver_email, message.as_string())

# 서버 연결 종료
server.quit()
