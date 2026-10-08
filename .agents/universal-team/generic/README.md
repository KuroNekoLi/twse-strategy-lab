# Generic runtime adapter

Map the role Registry to the platform's actual agent/session API. Verify separate agent identity/context, tools and data access, parallel limits, completion waiting and result collection. Identity separation alone is not a security sandbox; agent permissions follow platform policy. If no real delegation action exists, run serially and report `SEQUENTIAL_SINGLE_AGENT`.
