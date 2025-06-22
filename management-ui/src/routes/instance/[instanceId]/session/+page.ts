import { redirect } from '@sveltejs/kit';

import type { PageLoad } from './$types';

export const load = (async ({ params }) => {
	throw redirect(307, `/instance/${encodeURI(params.instanceId)}`);
}) satisfies PageLoad;
