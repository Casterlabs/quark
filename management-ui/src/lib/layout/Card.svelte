<script lang="ts">
	import { Box } from '@casterlabs/ui';

	let { href }: { href: string } = $props();
</script>

<a class="block w-64 h-24 m-1" {href}>
	<Box sides={['top', 'bottom', 'left', 'right']} class="h-full">
		<!-- svelte-ignore slot_element_deprecated -->
		<slot />
	</Box>
</a>
