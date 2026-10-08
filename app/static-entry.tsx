import { createRoot } from "react-dom/client";
import Home from "./page";

const root = document.getElementById("root");
if (!root) throw new Error("Missing application root element.");
createRoot(root).render(<Home />);
