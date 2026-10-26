import { fireEvent, render, screen } from '@testing-library/react';
import { EnrolmentCheckForm } from '../components/EnrolmentCheckForm';

function fill() {
  fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Jane Smith' } });
  fireEvent.change(screen.getByLabelText('Date of birth'), { target: { value: '1990-01-01' } });
  fireEvent.change(screen.getByLabelText('Postcode'), { target: { value: '3000' } });
  fireEvent.click(screen.getByRole('button', { name: 'Check' }));
}

function mockFetch(status: number, body: unknown) {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), { status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe('SEC-08 EnrolmentCheckForm', () => {
  it('sends details in a POST body, never in the URL, without a login token', async () => {
    const fetchMock = mockFetch(200, { enrolled: true });
    render(<EnrolmentCheckForm />);
    fill();
    expect(await screen.findByText('You are enrolled to vote.')).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/public/enrolment-check');
    expect(url).not.toContain('Jane');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ fullName: 'Jane Smith', dateOfBirth: '1990-01-01', postcode: '3000' });
    expect(new Headers(init.headers).has('Authorization')).toBe(false);
  });

  it('shows only a generic not-found message', async () => {
    mockFetch(200, { enrolled: false });
    render(<EnrolmentCheckForm />);
    fill();
    expect(await screen.findByText(/could not find an enrolment matching those exact details/)).toBeInTheDocument();
  });

  it('explains a rate limit', async () => {
    mockFetch(429, { error: 'TOO_MANY_REQUESTS' });
    render(<EnrolmentCheckForm />);
    fill();
    expect(await screen.findByText(/Too many checks/)).toBeInTheDocument();
  });
});
