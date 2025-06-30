<script lang="ts">
	import type { SessionId } from '$lib/quark';
	import type QuarkInstance from '$lib/quark';

	let { instance, sessionId }: { instance: QuarkInstance; sessionId: SessionId } = $props();
</script>

<audio class="w-full h-full" src={instance.sessionPlaybackUrl(sessionId, 'MP3')} controls playsinline muted autoplay></audio>
