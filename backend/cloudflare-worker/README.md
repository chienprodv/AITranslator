# Proxy production bằng Cloudflare Worker

Mục đích: API key OpenAI/DeepSeek nằm trên server, không nằm trong APK.

1. Cài Node.js và Wrangler: `npm i -g wrangler`.
2. Copy `wrangler.toml.example` thành `wrangler.toml`.
3. Đăng nhập: `wrangler login`.
4. Tạo các secret cần dùng:
   - `wrangler secret put AUTH_TOKEN`
   - `wrangler secret put DEEPSEEK_API_KEY`
   - hoặc `wrangler secret put OPENAI_API_KEY`
5. Deploy: `wrangler deploy`.
6. Trong app chọn `Server/Proxy → DeepSeek` hoặc `Server/Proxy → OpenAI`.
7. Nhập URL Worker, ví dụ `https://ngoc-tho-ai-translator.<account>.workers.dev` và token tương ứng.

Endpoint kiểm tra: `GET /health`.
Endpoint app gọi: `POST /translate`.
