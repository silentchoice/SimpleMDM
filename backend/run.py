"""Development server entry point."""

import uvicorn

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="127.0.0.1",
        port=18001,
        reload=False,
        log_level="info",
    )
