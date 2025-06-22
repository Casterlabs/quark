<script lang="ts">
	import LoadingSpinner from '$lib/layout/LoadingSpinner.svelte';
	import SessionVideoPlayer from '$lib/layout/SessionVideoPlayer.svelte';
	import { IconChevronDown, IconChevronRight } from '@casterlabs/heroicons-svelte';
	import { Box } from '@casterlabs/ui';

	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	let showStatsForNerds = $state(false);
</script>

{#await data.session}
	<div class="h-full flex items-center justify-center">
		<span class="text-3xl">
			<LoadingSpinner />
		</span>
	</div>
{:then session}
	{#if session}
		<div class="space-y-2 max-w-4xl mx-auto">
			<SessionVideoPlayer instance={data.instance} sessionId={session.id} />

			// TODO session end, egress management, external egress

			<Box class="mt-8" sides={['top', 'bottom', 'left', 'right']}>
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
		</div>
	{/if}
{/await}
