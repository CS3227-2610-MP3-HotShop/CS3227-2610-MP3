'use client';

import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useApplication } from '@/app/_providers/ApplicationProvider';
import { useApplicationRouter } from '@/app/_navigation/navigation';
import { useSubmission } from '@/app/_components/useSubmission';
import { Feedback } from '@/app/_components/Feedback';

function LoginRedirect() {
  const router = useApplicationRouter();
  useEffect(() => { router.replace('/login'); }, [router.replace]);
  return null;
}

/** Presentation guard only; backend reads and mutations must independently authorize the request. */
export function RequireSession({ children }: { readonly children: ReactNode }) {
  const { session } = useApplication();
  if (session.state === 'loading') return <main className="page"><p role="status">Loading…</p></main>;
  if (session.state === 'error') return <main className="page">
    <p role="alert" className="error">{session.message}</p>
    <button type="button" onClick={session.retry}>Retry</button>
  </main>;
  return session.user === null ? <LoginRedirect /> : children;
}

export function AppShell({ children }: { readonly children: ReactNode }) {
  const application = useApplication();
  const { session } = application;
  const router = useApplicationRouter();
  const pathname = usePathname();
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const logout = useSubmission({
    targetKey: session.state === 'ready' ? session.key : session.state,
    available: session.state === 'ready' && session.user !== null,
    execute: async () => {
      // BACKEND INTEGRATION: end the real session before clearing user caches and navigating.
      await application.logout();
      application.onSessionEnded();
      router.replace('/login');
      return { code: 'SUCCESS', message: 'Logged out.' };
    },
    onResult: result => {
      if (result.code === 'ERROR' || result.code === 'UNAVAILABLE') setLogoutError(result.message);
    },
  });
  const unread = session.state === 'ready' ? session.unreadCount : 0;
  const link = (label: string, href: string) =>
    <Link className="nav-button" href={href} aria-current={pathname === href ? 'page' : undefined}>{label}</Link>;
  return <div className="app-shell">
    <aside className="sidebar" aria-label="Marketplace sidebar">
      <div className="brand">HotShop</div>
      <nav className="navigation" aria-label="Marketplace navigation">
        {link('Search', '/search')}
        <div className="nav-heading">BUYING</div>
        {link('My Offers', '/offers')}
        {link('My Purchases', '/purchases')}
        <button className="nav-button" type="button" disabled>Wishlist — Coming soon</button>
        <div className="nav-heading">SELLING</div>
        {link('Dashboard', '/dashboard')}
        {link('My Listings', '/listings')}
        {link('My Sales', '/sales')}
        {link(unread > 0 ? `Conversations (${unread})` : 'Conversations', '/conversations')}
        {link('My Profile', '/profile')}
        <button className="logout-button" type="button" disabled={logout.busy} onClick={logout.submit}>Log out</button>
        <Feedback value={logoutError === null ? null : { kind: 'error', message: logoutError }} />
      </nav>
    </aside>
    <main className="main-content" id="main-content">{children}</main>
  </div>;
}

export function AccountLayout({ children }: { readonly children: ReactNode }) {
  return <main className="account-layout">{children}</main>;
}
