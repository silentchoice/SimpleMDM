# SimpleMDM — 主数据管理平台 Demo

SimpleMDM is a lightweight Master Data Management platform for enterprise personnel data governance.
This demo demonstrates the core workflow: data entry → approval → push to downstream systems.

## Quick Start

### Prerequisites
- Python 3.10+
- Node.js 18+

### One-Click Launch (Windows)
```
start.bat
```

### Manual Launch

**Backend:**
```bash
cd backend
pip install -r requirements.txt
python run.py
```
The API server starts at http://localhost:18001
Swagger docs at http://localhost:18001/docs

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```
The frontend starts at http://localhost:5173

## Demo Accounts

| Username | Password | Role | Description |
|---|---|---|---|
| wangwu | 123456 | HR Operator | Creates and edits personnel data |
| lisi | 123456 | HR Approver | Reviews and approves/rejects changes |
| zhaoliu | 123456 | Viewer | Read-only access for downstream system owners |

## Demo Script

1. Login as `wangwu` (operator) — browse personnel list
2. Edit Zhang San's record: change department from 工程部 to 产品部, position from 高级工程师 to 产品经理
3. Submit for approval — review the change diff dialog
4. Logout, login as `lisi` (approver) — check pending approvals
5. View change comparison, enter comment, click Approve
6. Check push logs — verify data was pushed to CRM and MES systems
7. Logout, login as `zhaoliu` (viewer) — verify data is updated, no edit permissions

## Tech Stack

- **Backend**: FastAPI (Python) + SQLAlchemy 2.0 + SQLite
- **Frontend**: Vue 3 + Element Plus + Vite
- **Auth**: JWT token-based

## Project Structure

```
simple-mdm/
├── backend/           # FastAPI application
│   ├── app/
│   │   ├── api/       # Route handlers
│   │   ├── models/    # SQLAlchemy models
│   │   ├── schemas/   # Pydantic schemas
│   │   └── services/  # Business logic
│   └── run.py
├── frontend/          # Vue 3 + Vite application
│   └── src/
│       ├── views/     # Page components
│       ├── layout/    # Main layout
│       ├── stores/    # Pinia state
│       └── api/       # Axios request modules
└── start.bat          # Windows one-click launcher
```

## Database

The demo uses SQLite (embedded, zero configuration). The database file is created automatically at `backend/simple_mdm.db` on first launch. Demo data is seeded automatically when the database is empty.

To migrate to MySQL/PostgreSQL, change the `DATABASE_URL` in `app/config.py`.
