import { goto } from '$app/navigation';
import { instances } from '$lib/config';

import type { LayoutLoad } from './$types';

export const ssr = false;

export const load = (async ({ params }) => {
	const instance = instances().filter((i) => i.id == params.instanceId)[0];
	if (!instance) goto('/');

	return { instance };
}) satisfies LayoutLoad;
