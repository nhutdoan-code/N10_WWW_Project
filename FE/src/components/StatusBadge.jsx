import { ORDER_STATUS, PRODUCT_STATUS, RETURN_STATUS } from '../config/constants';

export default function StatusBadge({ status }) {
  const label = ORDER_STATUS[status] || PRODUCT_STATUS[status] || RETURN_STATUS[status] || status;
  return <span className={`badge badge-${status}`}>{label}</span>;
}
