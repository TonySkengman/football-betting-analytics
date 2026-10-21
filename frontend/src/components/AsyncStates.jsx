import { AlertCircle, Inbox, LoaderCircle } from 'lucide-react';

export function LoadingState({ message = 'Loading football data…' }) {
  return <div className="state-box" role="status"><LoaderCircle className="animate-spin text-[#38BDF8]" size={26} /><p>{message}</p></div>;
}
export function EmptyState({ message = 'No matches found. Try another filter.' }) {
  return <div className="state-box" role="status"><Inbox size={26} /><p>{message}</p></div>;
}
export function ErrorState({ message = 'Unable to load data. Please try again.', onRetry }) {
  return <div className="state-box" role="alert"><AlertCircle className="text-rose-400" size={26} /><p>{message}</p>{onRetry && <button onClick={onRetry} className="secondary-button">Try again</button>}</div>;
}
