import { useEffect, useState } from "react";

type BotStatus = {
  state: string;
  lastCycleAt: string | null;
  lastError: string | null;
  consecutiveFailures: number;
  killSwitchActivatedAt: string | null;
  killSwitchReason: string | null;
};

type SchedulerStatus = {
  enabled: boolean;
  paused: boolean;
  executionInProgress: boolean;
  cron: string;
  zoneId: string;
  lastMessage: string;
  lastTrigger: string;
};

type Order = {
  id: string;
  market: string;
  side: string;
  status: string;
  quantity: number;
  priceKrw: number;
  createdAt: string;
};

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

function formatDate(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR", {
    hour12: false,
  });
}

export default function App() {
  const [botStatus, setBotStatus] = useState<BotStatus | null>(null);
  const [schedulerStatus, setSchedulerStatus] = useState<SchedulerStatus | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [bot, scheduler, orderList] = await Promise.all([
          getJson<BotStatus>("/api/bot/status"),
          getJson<SchedulerStatus>("/api/bot/scheduler"),
          getJson<Order[]>("/api/orders"),
        ]);
        if (cancelled) return;
        setBotStatus(bot);
        setSchedulerStatus(scheduler);
        setOrders(orderList.slice().reverse().slice(0, 8));
        setError(null);
      } catch (loadError) {
        if (cancelled) return;
        const message = loadError instanceof Error ? loadError.message : "failed to load";
        setError(message);
      }
    }

    load();
    const timer = window.setInterval(load, 5000);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return (
    <main className="shell">
      <section className="hero">
        <div>
          <p className="eyebrow">VaultTrade Control</p>
          <h1>운영 전환 전에 상태와 리스크를 한눈에 보는 관리 UI</h1>
        </div>
        <div className="hero-note">
          <span className="dot" />
          {error ? `API 연결 확인 필요: ${error}` : "API 자동 새로고침 중"}
        </div>
      </section>

      <section className="grid">
        <article className="panel stat-panel">
          <p className="label">Bot State</p>
          <strong>{botStatus?.state ?? "-"}</strong>
          <span>Last cycle: {formatDate(botStatus?.lastCycleAt ?? null)}</span>
        </article>

        <article className="panel stat-panel">
          <p className="label">Scheduler</p>
          <strong>{schedulerStatus?.paused ? "PAUSED" : "ACTIVE"}</strong>
          <span>{schedulerStatus?.cron ?? "-"}</span>
        </article>

        <article className="panel stat-panel">
          <p className="label">Failures</p>
          <strong>{botStatus?.consecutiveFailures ?? 0}</strong>
          <span>{botStatus?.lastError ?? "No recent error"}</span>
        </article>
      </section>

      <section className="content-grid">
        <article className="panel">
          <div className="panel-head">
            <h2>Recent Orders</h2>
            <span>{orders.length} rows</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Market</th>
                  <th>Side</th>
                  <th>Status</th>
                  <th>Qty</th>
                  <th>Price</th>
                </tr>
              </thead>
              <tbody>
                {orders.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="empty">
                      주문 데이터가 아직 없습니다.
                    </td>
                  </tr>
                ) : (
                  orders.map((order) => (
                    <tr key={order.id}>
                      <td>{formatDate(order.createdAt)}</td>
                      <td>{order.market}</td>
                      <td>{order.side}</td>
                      <td>{order.status}</td>
                      <td>{order.quantity}</td>
                      <td>{Number(order.priceKrw).toLocaleString("ko-KR")}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </article>

        <article className="panel">
          <div className="panel-head">
            <h2>Safety Snapshot</h2>
            <span>{schedulerStatus?.zoneId ?? "Asia/Seoul"}</span>
          </div>
          <ul className="signal-list">
            <li>
              <span>Kill switch</span>
              <strong>{botStatus?.killSwitchReason ?? "inactive"}</strong>
            </li>
            <li>
              <span>Kill switch at</span>
              <strong>{formatDate(botStatus?.killSwitchActivatedAt ?? null)}</strong>
            </li>
            <li>
              <span>Scheduler trigger</span>
              <strong>{schedulerStatus?.lastTrigger ?? "-"}</strong>
            </li>
            <li>
              <span>Scheduler note</span>
              <strong>{schedulerStatus?.lastMessage ?? "-"}</strong>
            </li>
          </ul>
        </article>
      </section>
    </main>
  );
}
