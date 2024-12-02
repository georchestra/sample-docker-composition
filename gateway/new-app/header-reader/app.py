from flask import Flask, request
import json
app = Flask(__name__)

def filter_headers(headers, prefix):
    return {key: value for key, value in headers.items() if key.lower().startswith(prefix)}

@app.route('/flask/')
def hello():
    return json.dumps(filter_headers(request.headers, 'sec-'), indent=4)

@app.route('/flask/admin')
def admin():
    return json.dumps(filter_headers(request.headers, 'sec-'), indent=4)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000)