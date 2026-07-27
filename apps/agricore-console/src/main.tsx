import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { App } from "./app/app";
import "./styles/index.css";

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("Không tìm thấy phần tử gốc #root để khởi tạo AgriCore Console.");
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
