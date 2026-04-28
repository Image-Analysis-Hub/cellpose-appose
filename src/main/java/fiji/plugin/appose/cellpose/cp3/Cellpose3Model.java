package fiji.plugin.appose.cellpose.cp3;

public enum Cellpose3Model
{
	// General cytoplasm models
	CYTO3( "cyto3", "General cytoplasm model trained on 9 datasets (Cellpose3). Works well for most cell types." ),
	CYTO2( "cyto2", "Older cytoplasm model trained on user-submitted images (Cellpose2)." ),
	CYTO( "cyto", "Original cytoplasm model trained only on the Cellpose training set (Cellpose1)." ),

	// Nucleus model
	NUCLEI( "nuclei", "Nuclear segmentation model. Trained with diameter 17.0 (others use 30.0)." ),

	// Dataset-specific models (Cellpose3)
	CYTO2_CP3( "cyto2_cp3", "Cellpose dataset-specific model (Cellpose3)." ),
	TISSUENET_CP3( "tissuenet_cp3", "TissueNet dataset-specific model (Cellpose3)." ),
	LIVECELL_CP3( "livecell_cp3", "LiveCell dataset-specific model (Cellpose3)." ),
	YEAST_PHC_CP3( "yeast_PhC_cp3", "YEAZ phase contrast yeast dataset-specific model (Cellpose3)." ),
	YEAST_BF_CP3( "yeast_BF_cp3", "YEAZ brightfield yeast dataset-specific model (Cellpose3)." ),
	BACT_PHASE_CP3( "bact_phase_cp3", "Omnipose phase contrast bacteria dataset-specific model (Cellpose3)." ),
	BACT_FLUOR_CP3( "bact_fluor_cp3", "Omnipose fluorescence bacteria dataset-specific model (Cellpose3)." ),
	DEEPBACS_CP3( "deepbacs_cp3", "DeepBacs dataset-specific model (Cellpose3)." ),

	// Transformer models
	TRANSFORMER_CP3( "transformer_cp3", "Transformer-based model (Cellpose3). Takes 3 input channels." ),
	NEURIPS_CELLPOSE_TRANSFORMER( "neurips_cellpose_transformer",
			"Transformer model from NeurIPS challenge. Takes 3 input channels." ),
	NEURIPS_CELLPOSE_DEFAULT( "neurips_cellpose_default",
			"U-Net based model from NeurIPS challenge. Takes 3 input channels." ),
	NEURIPS_GRAYSCALE_CYTO2( "neurips_grayscale_cyto2",
			"Grayscale cytoplasm model from NeurIPS challenge." ),

	// Legacy Cellpose2 style-specific models
	CP( "CP", "Legacy Cellpose2 style-specific model (version 1)." ),
	CPX( "CPx", "Legacy Cellpose2 style-specific model (version 2)." ),
	TN1( "TN1", "Legacy TissueNet style-specific model (Cellpose2)." ),
	TN2( "TN2", "Legacy TissueNet style-specific model (Cellpose2)." ),
	TN3( "TN3", "Legacy TissueNet style-specific model (Cellpose2)." ),
	LC1( "LC1", "Legacy LiveCell style-specific model (Cellpose2)." ),
	LC2( "LC2", "Legacy LiveCell style-specific model (Cellpose2)." ),
	LC3( "LC3", "Legacy LiveCell style-specific model (Cellpose2)." ),
	LC4( "LC4", "Legacy LiveCell style-specific model (Cellpose2)." );

	private final String name;

	private final String description;

	Cellpose3Model( final String name, final String description )
	{
		this.name = name;
		this.description = description;
	}

	public String modelName()
	{
		return name;
	}

	public String description()
	{
		return description;
	}

	@Override
	public String toString()
	{
		return name;
	}

	/**
	 * Gets a tooltip string combining the model name and description
	 * 
	 * @return Formatted string for tooltip display
	 */
	public String getTooltip()
	{
		return String.format( "%s: %s", name, description );
	}
}