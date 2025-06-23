<script lang="ts">
	import { fade } from 'svelte/transition';

	import { IconXMark } from '@casterlabs/heroicons-svelte';
	import { Box } from '@casterlabs/ui';

	let { showModal = $bindable(false) } = $props();
</script>

{#if showModal}
	<div class="absolute inset-0 bg-[#00000099] flex items-center justify-center" transition:fade={{ duration: 75 }}>
		<button class="absolute inset-0" onclick={() => (showModal = false)} aria-hidden="true"></button>

		<div aria-modal="true">
			<Box sides={['top', 'bottom', 'left', 'right']} class="space-y-4 w-72 z-10 relative">
				<!-- svelte-ignore slot_element_deprecated -->
				<slot />

				<button class="absolute right-1 top-0 cursor-pointer" onclick={() => (showModal = false)}>
					<span class="sr-only"> Close </span>
					<IconXMark class="inline align-middle" theme="mini" />
				</button>
			</Box>
		</div>
	</div>
{/if}
