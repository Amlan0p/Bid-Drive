import express, { type Express } from "express";
import cors from "cors";
import pinoHttp from "pino-http";
import path from "path";
import router from "./routes";
import { logger } from "./lib/logger";

const app: Express = express();

app.use(
  pinoHttp({
    logger,
    serializers: {
      req(req) {
        return {
          id: req.id,
          method: req.method,
          url: req.url?.split("?")[0],
        };
      },
      res(res) {
        return {
          statusCode: res.statusCode,
        };
      },
    },
  }),
);

app.disable("x-powered-by");
app.set("trust proxy", 1);
// Never serialise password hashes, whatever route returns a user row.
app.set("json replacer", (key: string, value: unknown) => (key === "passwordHash" ? undefined : value));

// CORS: native Android clients do not need CORS. Only browser clients (admin dashboard) do.
const allowed = (process.env.CORS_ORIGINS ?? "").split(",").map((o) => o.trim()).filter(Boolean);
app.use(cors({ origin: allowed.length > 0 ? allowed : process.env.NODE_ENV === "production" ? false : true }));
app.use(express.json({ limit: "100kb" }));
app.use(express.urlencoded({ extended: true }));

app.use("/api/cars/images", express.static(path.join(process.cwd(), "public/cars")));
app.use("/api", router);

export default app;
