import { Spin } from 'antd';
import { Navigate, Outlet, useLocation } from 'react-router';
import { useMe } from '../hooks/queries';

/** Route guard. The server still authorizes every request; this only steers navigation. */
export function RequireAuth() {
  const me = useMe();
  const location = useLocation();

  if (me.isPending) {
    return (
      <div style={{ height: '100%', display: 'grid', placeItems: 'center' }}>
        <Spin aria-label="正在加载" />
      </div>
    );
  }
  if (!me.data) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <Outlet />;
}
