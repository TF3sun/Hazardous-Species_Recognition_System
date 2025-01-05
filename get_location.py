from flask import Flask, request, jsonify
import json
import os
import sqlite3
from datetime import datetime

app = Flask(__name__)

@app.route('/save_json', methods=['POST'])
def save_json():
    try:
        # 클라이언트로부터 JSON 데이터 수신
        json_data = request.json

        # json 데이터 분리
        name = json_data.get('name')
        latitude = json_data.get('latitude')
        longitude = json_data.get('longitude')
        accuracy = json_data.get('accuracy')
        time = datetime.now().strftime('%Y-%m-%d')  # Current system time

        # 파일 저장할 디렉토리 위치
        data_dir_path = './LocationData/'
        file_list = os.listdir(data_dir_path)
        size = len(file_list)
        
        # JSON 데이터를 파일로 저장
        with open(f'{data_dir_path}data{size}.json', 'w') as json_file:
            json.dump(json_data, json_file)
        
        # 데이터베이스 저장
        SaveToDB(name, latitude, longitude, time)        

        # 데이터 수신 결과 출력
        print(f"Received JSON data: {json_data}")
        print(f"Latitude: {latitude}, Longitude: {longitude}, Accuracy: {accuracy}, Time: {time}")

        return jsonify({'status': 'success', 'message': 'JSON data saved successfully'})

    except Exception as e:
        print(f"Error: {e}")
        return jsonify({'status': 'error', 'message': str(e)})
    
# 데이터베이스에 전송 받은 json 데이터 저장
def SaveToDB(name, latitude, longitude, time):
    conn = sqlite3.connect("location.db")
    cur = conn.cursor()
    
    # 테이블이 존재하지 않는 경우에만 생성
    cur.execute('CREATE TABLE IF NOT EXISTS location_data(id INTEGER PRIMARY KEY, name TEXT, latitude REAL, longitude REAL, time TEXT)')
    
    # INSERT 문 수정: id는 INTEGER PRIMARY KEY이므로 자동으로 증가하므로 None으로 설정
    cur.execute('INSERT INTO location_data VALUES (NULL, ?, ?, ?, ?)', (name, latitude, longitude, time))
    
    conn.commit()
    conn.close()

if __name__ == '__main__':
    # 서버 실행
    app.run(host='0.0.0.0', port=5000)