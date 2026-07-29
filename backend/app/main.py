"""FastAPI application factory."""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import engine, Base, SessionLocal
from app.seed import seed_all

# Import route modules
from app.api import auth, personnel, approvals, push_logs, dashboard, users, push_apis


def create_app() -> FastAPI:
    """Create and configure the FastAPI application."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        # Startup: create tables and seed data
        Base.metadata.create_all(bind=engine)
        db = SessionLocal()
        try:
            seed_all(db)
        finally:
            db.close()
        yield
        # Shutdown: nothing special needed

    app = FastAPI(
        title=settings.APP_NAME,
        version=settings.APP_VERSION,
        lifespan=lifespan,
    )

    # CORS — allow all origins for dev/demo
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Register routers
    app.include_router(auth.router)
    app.include_router(personnel.router)
    app.include_router(approvals.router)
    app.include_router(push_logs.router)
    app.include_router(dashboard.router)
    app.include_router(users.router)
    app.include_router(push_apis.router)

    return app


app = create_app()
