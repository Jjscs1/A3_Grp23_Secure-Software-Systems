import { render, screen } from '@testing-library/react';
import { CandidateList } from '../components/CandidateList';

describe('SEC-07 CandidateList', () => {
  it('renders a candidate named <script>alert(1)</script> as text', () => {
    const { container } = render(
      <CandidateList
        candidates={[{ id: 1, name: '<script>alert(1)</script>', party: '<img src=x onerror=alert(1)>' }]}
      />,
    );

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
  });

  it('shows a message when there are no candidates', () => {
    render(<CandidateList candidates={[]} />);
    expect(screen.getByText(/No candidates/)).toBeInTheDocument();
  });
});
