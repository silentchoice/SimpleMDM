"""Application configuration."""

import os


class Settings:
    APP_NAME: str = "SimpleMDM"
    APP_VERSION: str = "0.1.0"

    # Database — SQLite for demo, swap to MySQL/PostgreSQL for production
    BASE_DIR: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        f"sqlite:///{os.path.join(BASE_DIR, 'simple_mdm.db')}",
    )

    # JWT
    SECRET_KEY: str = os.getenv("SECRET_KEY", "change-me-in-production")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 hours for demo

    # Push simulation
    PUSH_SUCCESS_RATE: float = 0.9  # 90% simulated success
    PUSH_TARGET_SYSTEMS: list = ["CRM", "MES"]


settings = Settings()
