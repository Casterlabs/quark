<span class="inline-block w-[1em] h-[1em] align-middle">
	<span class="loader"></span>
</span>

<style>
	.loader {
		width: 100%;
		height: 100%;
		border-radius: 50%;
		display: inline-block;
		border-top: 3px solid var(--clui-color-base-8);
		border-right: 3px solid transparent;
		margin-bottom: 3px;
		box-sizing: border-box;
		animation: rotation 1s linear infinite;
	}

	@keyframes rotation {
		0% {
			transform: rotate(0deg);
		}

		100% {
			transform: rotate(360deg);
		}
	}
</style>
