<script lang="ts">
	import type { Session } from '$lib/quark';
	import type QuarkInstance from '$lib/quark';

	import StreamingFLV from '$lib/layout/player/StreamingFLV.svelte';
	import StreamingMP3 from '$lib/layout/player/StreamingMP3.svelte';
	import { IconPlay } from '@casterlabs/heroicons-svelte';
	import { Box } from '@casterlabs/ui';

	let { instance, session }: { instance: QuarkInstance; session: Session } = $props();

	let playing = $state(false);
</script>

<Box class="overflow-hidden relative aspect-video" sides={['top', 'bottom', 'left', 'right']} style="padding: 0 !important;">
	{#if playing}
		{#if session.info.video.length == 0}
			<StreamingMP3 {instance} sessionId={session.id} />
		{:else}
			<StreamingFLV {instance} sessionId={session.id} />
		{/if}
	{:else}
		<img aria-hidden="true" alt="" class="w-full h-full object-contain" src={instance.sessionThumbnailUrl(session.id)} />

		<button class="absolute inset-0 cursor-pointer flex items-center justify-center bg-[#000000aa]" onclick={() => (playing = true)}>
			<span class="sr-only">Click to play</span>
			<IconPlay class="w-12 h-12" theme="solid" />
		</button>
	{/if}
</Box>
