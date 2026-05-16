export interface Show {
  id: string
  title: string
  subtitle: string
  coverUrl: string
  description: string
  duration: number
  category: 'Movie' | 'Musical' | 'Play' | 'Concert' | 'Ballet'
  tags: string[]
  status?: 'ON_SALE' | 'COMING_SOON'
}

export interface Schedule {
  id: string
  showId: string
  theaterName: string
  startTime: string
  endTime: string
  status: 'ON_SALE' | 'SOLD_OUT' | 'PREPARING' | 'COMING_SOON'
  priceRange: string
}

export const mockShows: Show[] = [
  {
    id: 's-001',
    title: 'THE PHANTOM OF THE OPERA',
    subtitle: 'Classic Musical',
    coverUrl: 'https://images.unsplash.com/photo-1507676184212-d0330a157088?q=80&w=1000&auto=format&fit=crop',
    description: 'The brilliant original production of Andrew Lloyd Webber\'s classic musical.',
    duration: 150,
    category: 'Musical',
    tags: ['Classic', 'Must See'],
    status: 'ON_SALE'
  },
  {
    id: 's-002',
    title: 'SWAN LAKE',
    subtitle: 'Tchaikovsky Ballet',
    coverUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=80&w=1000&auto=format&fit=crop',
    description: 'A masterpiece of classical ballet with a timeless score.',
    duration: 120,
    category: 'Ballet',
    tags: ['Dance', 'Romantic'],
    status: 'ON_SALE'
  },
  {
    id: 's-003',
    title: 'COLDPLAY: MUSIC OF THE SPHERES',
    subtitle: 'World Tour Concert',
    coverUrl: 'https://images.unsplash.com/photo-1540039155732-684736dd61dc?q=80&w=1000&auto=format&fit=crop',
    description: 'Experience the magic of Coldplay live in concert.',
    duration: 180,
    category: 'Concert',
    tags: ['Live', 'Pop'],
    status: 'COMING_SOON'
  },
  {
    id: 's-004',
    title: 'DUNE: PART TWO',
    subtitle: 'Sci-Fi Epic',
    coverUrl: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=1000&auto=format&fit=crop',
    description: 'The saga continues as Paul Atreides unites with Chani and the Fremen.',
    duration: 166,
    category: 'Movie',
    tags: ['Sci-Fi', 'IMAX'],
    status: 'COMING_SOON'
  }
]

export const mockSchedules: Record<string, Schedule[]> = {
  's-001': [
    { id: 'sch-101', showId: 's-001', theaterName: 'Main Hall', startTime: '2026-05-24T19:30:00Z', endTime: '2026-05-24T22:00:00Z', status: 'ON_SALE', priceRange: '$50 - $150' },
    { id: 'sch-102', showId: 's-001', theaterName: 'Main Hall', startTime: '2026-05-25T14:00:00Z', endTime: '2026-05-25T16:30:00Z', status: 'ON_SALE', priceRange: '$50 - $150' }
  ],
  's-002': [
    { id: 'sch-201', showId: 's-002', theaterName: 'Opera House', startTime: '2026-06-10T20:00:00Z', endTime: '2026-06-10T22:00:00Z', status: 'ON_SALE', priceRange: '$80 - $200' }
  ],
  's-003': [
    { id: 'sch-301', showId: 's-003', theaterName: 'Grand Stadium', startTime: '2026-08-15T20:00:00Z', endTime: '2026-08-15T23:00:00Z', status: 'PREPARING', priceRange: '$120 - $500' }
  ],
  's-004': [
    { id: 'sch-401', showId: 's-004', theaterName: 'IMAX Cinema', startTime: '2026-09-01T14:00:00Z', endTime: '2026-09-01T17:00:00Z', status: 'COMING_SOON', priceRange: '$20 - $30' }
  ]
}
