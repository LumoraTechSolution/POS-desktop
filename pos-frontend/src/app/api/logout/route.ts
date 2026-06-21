import { NextResponse } from 'next/server';

// Clears the httpOnly `auth-token` cookie locally without depending on the backend.
// Needed so logout works even if the backend is down / restarted — otherwise the
// middleware keeps redirecting /login -> /overview because the cookie persists.
export async function POST() {
  const response = NextResponse.json({ ok: true });
  response.cookies.set('auth-token', '', {
    httpOnly: true,
    maxAge: 0,
    path: '/',
    sameSite: 'lax',
  });
  return response;
}
