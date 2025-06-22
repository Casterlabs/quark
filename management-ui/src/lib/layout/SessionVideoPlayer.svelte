<script lang="ts">
	import type { SessionId } from '$lib/quark';
	import type QuarkInstance from '$lib/quark';
	import mpegts from 'mpegts.js';

	import { Box } from '@casterlabs/ui';

	import { onMount } from 'svelte';

	let { instance, sessionId }: { instance: QuarkInstance; sessionId: SessionId } = $props();

	let videoElement: HTMLVideoElement;

	onMount(() => {
		const playbackURL = instance.sessionPlaybackUrl(sessionId, 'FLV');

		const player = mpegts.createPlayer({
			type: 'flv',
			isLive: true,
			url: playbackURL
		});
		player.attachMediaElement(videoElement);
		player.load();
		player.play();

		return () => player.destroy();
	});
</script>

<Box class="overflow-hidden" sides={['top', 'bottom', 'left', 'right']} style="padding: 0 !important;">
	<video class="w-full h-auto" bind:this={videoElement} controls playsinline muted autoplay></video>
</Box>
