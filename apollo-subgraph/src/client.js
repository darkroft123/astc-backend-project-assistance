const PROJECT_ASSISTANCE_URL = process.env.PROJECT_URL;

function normalizeValue(value) {
  if (Array.isArray(value)) {
    return value.map(normalizeValue);
  }

  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, innerValue]) => [key, normalizeValue(innerValue)])
    );
  }

  if (typeof value === 'bigint') {
    return value.toString();
  }

  return value;
}

export async function callProjectAssistance(query, variables = {}, token = '') {
  if (!PROJECT_ASSISTANCE_URL) {
    throw new Error('Falta PROJECT_ASSISTANCE_GRAPHQL_URL en el .env del subgraph');
  }

  const headers = {
    'Content-Type': 'application/json'
  };

  if (token) {
    headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  }

  const response = await fetch(PROJECT_ASSISTANCE_URL, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      query,
      variables: normalizeValue(variables)
    })
  });

  const text = await response.text();
  let payload;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new Error(`Respuesta no-JSON de project-assistance (HTTP ${response.status}): ${text.slice(0, 200)}`);
  }

  if (!response.ok) {
    throw new Error(payload?.errors?.map((error) => error.message).join(' | ') || `Error HTTP ${response.status} llamando a project-assistance`);
  }

  if (payload.errors?.length) {
    throw new Error(payload.errors.map((error) => error.message).join(' | '));
  }

  return payload.data;
}