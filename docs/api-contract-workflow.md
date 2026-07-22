# Quy trình quản trị hợp đồng API REST

## Xuất và kiểm tra snapshot

Khởi động BE với PostgreSQL local/test đã có migration:

```powershell
node scripts/export-openapi-contract.cjs --url http://localhost:1606/v3/api-docs
node scripts/export-openapi-contract.cjs --url http://localhost:1606/v3/api-docs --check
```

Snapshot chuẩn hóa nằm ở `contracts/openapi/erg-openapi.json`; checksum nằm ở file `.sha256`. Postman đọc snapshot này và chỉ ghi output dưới `postman/`.

## Chặn breaking change

```powershell
node scripts/check-openapi-breaking.cjs --base tmp/erg-openapi-base.json --revision contracts/openapi/erg-openapi.json
```

Pull request CI tự động chạy hai gate:

1. `export-openapi-contract.cjs --file ... --check` xác nhận snapshot và SHA-256 đã commit không bị stale.
2. Trên pull request, snapshot của `base_ref` được so sánh với revision bằng image `tufin/oasdiff:v1.15.1`; warning breaking change là failure.

Push branch không có base snapshot để so sánh nhưng vẫn phải qua gate checksum. Khi thay đổi endpoint, commit snapshot và checksum cùng thay đổi; pull request phải có migration/deprecation evidence nếu contract breaking.

CI phải giữ nguyên exit code của `oasdiff`; cảnh báo breaking cũng làm gate thất bại.

## Chính sách thay đổi

Thay đổi theo chuỗi: **add → deprecate → migrate → observe → remove**. Field mới nên optional trước; rename/xóa/type change phải có compatibility hoặc version endpoint. Mọi snapshot và checksum phải commit cùng thay đổi BE.
