<script lang="ts">
	import type { SessionId } from '$lib/quark';
	import type QuarkInstance from '$lib/quark';
	import mpegts from 'mpegts.js';

	import { IconPlay } from '@casterlabs/heroicons-svelte';
	import { Box } from '@casterlabs/ui';

	import { onDestroy } from 'svelte';

	let { instance, sessionId }: { instance: QuarkInstance; sessionId: SessionId } = $props();

	let mpegtsPlayer = $state<mpegts.Player | null>(null);

	let videoElement: HTMLVideoElement;
	let playing = $state(false);

	function play() {
		const playbackURL = instance.sessionPlaybackUrl(sessionId, 'FLV');

		playing = true;

		mpegtsPlayer = mpegts.createPlayer({
			type: 'flv',
			isLive: true,
			url: playbackURL
		});
		mpegtsPlayer.attachMediaElement(videoElement);
		mpegtsPlayer.load();
		mpegtsPlayer.play();
	}

	onDestroy(() => {
		if (mpegtsPlayer) {
			mpegtsPlayer.destroy();
		}
	});
</script>

<Box class="overflow-hidden relative" sides={['top', 'bottom', 'left', 'right']} style="padding: 0 !important;">
	<video
		aria-hidden={!playing}
		class="w-full h-auto"
		poster={instance.sessionThumbnailUrl(sessionId)}
		bind:this={videoElement}
		controls={playing}
		playsinline
		muted
		autoplay
	></video>
	{#if !playing}
		<button class="absolute inset-0 cursor-pointer flex items-center justify-center bg-[#000000aa]" onclick={play}>
			<span class="sr-only">Click to play</span>
			<IconPlay class="w-12 h-12" theme="solid" />
		</button>
	{/if}
</Box>
