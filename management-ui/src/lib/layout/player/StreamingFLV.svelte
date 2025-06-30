<script lang="ts">
	import type { SessionId } from '$lib/quark';
	import type QuarkInstance from '$lib/quark';
	import mpegts from 'mpegts.js';

	import { onDestroy, onMount } from 'svelte';

	let { instance, sessionId }: { instance: QuarkInstance; sessionId: SessionId } = $props();

	let mpegtsPlayer = $state<mpegts.Player | null>(null);
	let videoElement: HTMLVideoElement;

	onMount(() => {
		const playbackURL = instance.sessionPlaybackUrl(sessionId, 'FLV');

		mpegtsPlayer = mpegts.createPlayer({
			type: 'flv',
			isLive: true,
			url: playbackURL
		});
		mpegtsPlayer.attachMediaElement(videoElement);
		mpegtsPlayer.load();
		mpegtsPlayer.play();
	});

	onDestroy(() => {
		if (mpegtsPlayer) {
			mpegtsPlayer.destroy();
		}
	});
</script>

<video class="w-full h-full object-contain" poster={instance.sessionThumbnailUrl(sessionId)} bind:this={videoElement} controls playsinline muted autoplay
></video>
