# s3browser

A simple S3 browser built with Java 21, Spring Boot, and AWS SDK v2.

## Features

- Connection form (region, access/secret key, session token, endpoint override, path-style)
- Bucket listing
- Bucket creation
- Bucket deletion (with confirmation)
- Folder and object listing inside a bucket (prefix + delimiter)
- Folder creation
- Object metadata viewing
- Object upload
- Storage class selection during upload
- Automatic multipart upload for large files (size-based)
- Object download
- Object copy (within the same bucket)
- Object deletion (uses a confirmation page, does not delete directly)
- Folder deletion (confirmed and recursive)
- Pending/running uploads tracking page

## Architecture (Hexagonal)

- `domain`: Business rules and model records
- `application`: Use cases and ports
- `infrastructure`: AWS SDK v2 adapter
- `web`: Thymeleaf UI and controller adapter

## Run

Requirements:

- Java 21
- Maven 3.9+

```bash
cd /Users/soner/zextras/awsclient
mvn spring-boot:run
```

UI: `http://localhost:8080/connect`

## Test

```bash
cd /Users/soner/zextras/awsclient
mvn test
```

## Notes

- If you do not provide access/secret key, the app uses the AWS default credential chain.
- For services like MinIO, you can use endpoint + path-style options.
- Memory usage may increase for large files because the download endpoint currently returns `byte[]` as an MVP approach.
- Upload list is kept in server memory (it is cleared when the app restarts).

## Docker: Persistent Connections (No DB)

- `docker-compose.yml` mounts a named volume at `/app/data`.
- The app stores saved connections at `/app/data/connections.json` via `APP_CONNECTIONS_STORE_FILE`.
- This keeps saved connections after container restart/recreate.

Run with compose:

```bash
cd /Users/soner/zextras/s3browser
docker compose up -d --build
```

