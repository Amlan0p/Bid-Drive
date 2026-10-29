import { Router, type IRouter } from "express";
import healthRouter from "./health";
import authRouter from "./auth";
import usersRouter from "./users";
import carsRouter from "./cars";
import auctionsRouter from "./auctions";
import watchlistRouter from "./watchlist";
import paymentsRouter from "./payments";
import notificationsRouter from "./notifications";
import analyticsRouter from "./analytics";
import meRouter from "./me";

const router: IRouter = Router();

router.use(healthRouter);
router.use(authRouter);
router.use(meRouter);
router.use(usersRouter);
router.use(carsRouter);
router.use(auctionsRouter);
router.use(watchlistRouter);
router.use(paymentsRouter);
router.use(notificationsRouter);
router.use(analyticsRouter);

export default router;
