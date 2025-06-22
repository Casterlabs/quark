<script lang="ts">
	import Card from '$lib/layout/Card.svelte';
	import LoadingSpinner from '$lib/layout/LoadingSpinner.svelte';

	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<div class="h-screen flex items-center justify-center">
	{#await data.instance.listSessions()}
		<span class="text-3xl">
			<LoadingSpinner />
		</span>
	{:then ids}
		<ul class="max-w-2xl justify-center flex flex-wrap">
			{#each ids as sid}
				<li>
					<Card href="/instance/{encodeURI(data.instance.id)}/session/{encodeURI(sid)}">
						<div class="h-full flex items-center justify-center">
							{sid}
						</div>
					</Card>
				</li>
			{/each}
		</ul>
	{/await}
</div>
