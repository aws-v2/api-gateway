import jwt
import datetime
import base64

# The secret from target/classes/application-dev.yml
SECRET_BASE64 = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
USER_ID = "b2c3d4e5-2222-4a5f-9c82-1d4e6b8f3a22"

def generate_jwt(user_id, secret_base64):
    # Java code uses Decoders.BASE64.decode(secret) to get key bytes
    key_bytes = base64.b64decode(secret_base64)
    
    # Expiration set for 24h as per application-dev.yml (86400000ms)
    now = datetime.datetime.utcnow()
    exp = now + datetime.timedelta(days=1)
    
    payload = {
        "userId": user_id,
        "sub": "test-user",
        "role": "ADMIN",  # Adding role to ensure it passes through all filters
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp())
    }
    
    # We use HS512 because the key is 512 bits (64 bytes)
    # The Java JJWT library automatically picks HS512 for a 512-bit key
    token = jwt.encode(payload, key_bytes, algorithm="HS512")
    return token

if __name__ == "__main__":
    token = generate_jwt(USER_ID, SECRET_BASE64)
    print(token)
