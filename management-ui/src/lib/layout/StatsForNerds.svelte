<script lang="ts">
	import type { Session } from '$lib/quark';

	import { IconChevronDown, IconChevronRight } from '@casterlabs/heroicons-svelte';
	import { Box } from '@casterlabs/ui';

	let { session }: { session: Session } = $props();

	let showStatsForNerds = $state(false);
</script>

<Box sides={['top', 'bottom', 'left', 'right']}>
	<button class="block w-full text-left cursor-pointer" onclick={() => (showStatsForNerds = !showStatsForNerds)}>
		{#if showStatsForNerds}
			<IconChevronDown class="inline-block align-bottom" theme="mini" />
		{:else}
			<IconChevronRight class="inline-block align-bottom" theme="mini" />
		{/if}
		Stats for nerds
	</button>

	{#if showStatsForNerds}
		<br />
		<ul class="space-y-2">
			{#each session.info.video as feed}
				<li>
					<p>Video Feed #{feed.id}</p>
					<pre>{JSON.stringify(feed, null, 2)}</pre>
				</li>
			{/each}
			{#each session.info.audio as feed}
				<li>
					<p>Audio Feed #{feed.id}</p>
					<pre>{JSON.stringify(feed, null, 2)}</pre>
				</li>
			{/each}
		</ul>
	{/if}
</Box>
